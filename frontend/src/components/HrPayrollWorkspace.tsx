import { useEffect, useMemo, useState } from 'react';
import { CheckCheck, Download, Plus, RefreshCw } from 'lucide-react';
import { api, SalesRow } from '../api';

const value = (row: Record<string, unknown>, ...keys: string[]) => {
  for (const key of keys) {
    if (row[key] !== undefined && row[key] !== null) return row[key];
    const actual = Object.keys(row).find(candidate => candidate.toLowerCase() === key.toLowerCase());
    if (actual) return row[actual];
  }
  return '-';
};
const money = (amount: unknown) => `₹${Number(amount || 0).toLocaleString('en-IN')}`;

export function HrPayrollWorkspace({ module }: { module: string }) {
  const [dashboard, setDashboard] = useState<Record<string, unknown> | null>(null);
  const [rows, setRows] = useState<SalesRow[]>([]);
  const [run, setRun] = useState<SalesRow | null>(null);
  const [items, setItems] = useState<SalesRow[]>([]);
  const [runMonth, setRunMonth] = useState(new Date().toISOString().slice(0, 7) + '-01');
  const [paymentMode, setPaymentMode] = useState('BANK_TRANSFER');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const load = async (selectedRunId?: string) => {
    setBusy(true); setError('');
    try {
      const dashboardData = await api.hrDashboard(); setDashboard(dashboardData);
      const result = module === 'Employees' ? await api.hrEmployees() : module === 'Attendance' ? await api.hrAttendance() : module === 'Leave' ? await api.hrLeave() : await api.hrPayrollRuns();
      setRows(result);
      if (module === 'Payroll' || module === 'Salary') {
        const selected = result.find(row => String(value(row, 'id')) === selectedRunId) || result[0];
        setRun(selected || null);
        setItems(selected ? await api.hrPayrollItems(String(value(selected, 'id'))) : []);
      }
    } catch (err) { setError(err instanceof Error ? err.message : 'HR workspace could not be loaded.'); }
    finally { setBusy(false); }
  };
  useEffect(() => { load(); }, [module]);

  const createRun = async () => {
    setBusy(true); setError('');
    try { const created = await api.createPayrollRun(runMonth); await load(String(value(created, 'id'))); }
    catch (err) { setError(err instanceof Error ? err.message : 'Payroll run could not be created.'); setBusy(false); }
  };
  const lockRun = async () => {
    if (!run) return; setBusy(true); setError('');
    try { await api.finalizePayroll(String(value(run, 'id'))); await load(String(value(run, 'id'))); }
    catch (err) { setError(err instanceof Error ? err.message : 'Payroll could not be locked.'); setBusy(false); }
  };
  const pay = async (item: SalesRow) => {
    setBusy(true); setError('');
    try { await api.paySalary(String(value(item, 'id')), paymentMode); await load(String(value(run || {}, 'id'))); }
    catch (err) { setError(err instanceof Error ? err.message : 'Salary could not be paid.'); setBusy(false); }
  };
  const approve = async (row: SalesRow, approveRequest: boolean) => {
    setBusy(true); setError('');
    try { await api.approveLeave(String(value(row, 'id')), approveRequest); await load(); }
    catch (err) { setError(err instanceof Error ? err.message : 'Leave request could not be updated.'); setBusy(false); }
  };
  const columns = useMemo(() => rows.length ? Object.keys(rows[0]) : [], [rows]);
  const payroll = module === 'Payroll' || module === 'Salary';
  const pending = items.filter(item => String(value(item, 'paymentStatus')).toUpperCase() === 'PENDING');

  return <div className="page">
    <div className="page-top"><div><p className="eyebrow">PEOPLE & PAYROLL</p><h2>{module}</h2><p className="muted">Controlled employee, attendance, leave, payroll locking, and salary payment workflow.</p></div><div className="page-actions"><button className="secondary" onClick={() => downloadCsv(`hr-${module.toLowerCase()}.csv`, rows)}><Download size={16}/>Export</button><button className="secondary" onClick={() => load()} disabled={busy}><RefreshCw size={16}/>Refresh</button>{payroll && <><input className="filter" type="date" value={runMonth} onChange={event => setRunMonth(event.target.value)} /><button className="secondary" onClick={createRun} disabled={busy}><Plus size={16}/>Create run</button>{run && String(value(run, 'status')) === 'DRAFT' && <button className="primary" onClick={lockRun} disabled={busy}>Lock payroll</button>}</>}</div></div>
    {error && <p className="error">{error}</p>}
    {dashboard && <section className="stat-grid"><article className="stat-card"><p>Employees</p><h3>{String(value(dashboard, 'employees'))}</h3><span>Active workforce</span></article><article className="stat-card gold"><p>Present today</p><h3>{String(value(dashboard, 'presentToday'))}</h3><span>Attendance records</span></article><article className="stat-card blue"><p>Leave pending</p><h3>{String(value(dashboard, 'leavePending'))}</h3><span>Approval queue</span></article><article className="stat-card coral"><p>Salary pending</p><h3>{money(value(dashboard, 'salaryPending'))}</h3><span>{String(value(dashboard, 'payrollStatus'))}</span></article></section>}
    <section className="card recent"><div className="card-head"><div><p className="eyebrow">CONNECTED HR DATA</p><h3>{rows.length} records</h3></div><span className="count">{busy ? 'SYNCING' : 'LIVE'}</span></div>{rows.length ? <div className="table-wrap"><table><thead><tr>{columns.map(column => <th key={column}>{column.replaceAll('_', ' ')}</th>)}{module === 'Leave' && <th>Action</th>}</tr></thead><tbody>{rows.map((row, index) => <tr key={index}>{columns.map(column => <td key={column}>{String(value(row, column)).replaceAll('_', ' ')}</td>)}{module === 'Leave' && String(value(row, 'status')).toUpperCase() === 'PENDING' && <td><button className="text-button" onClick={() => approve(row, true)} disabled={busy}><CheckCheck size={14}/>Approve</button><button className="text-button" onClick={() => approve(row, false)} disabled={busy}>Reject</button></td>}</tr>)}</tbody></table></div> : <p className="empty">No HR records available.</p>}</section>
    {payroll && run && <section className="card recent"><div className="card-head"><div><p className="eyebrow">PAYROLL RUN · {String(value(run, 'runMonth'))}</p><h3>{items.length} salary items</h3></div><span className="count">{String(value(run, 'status'))}</span></div><div className="inventory-grid">{items.map(item => <article className="unit-tile" key={String(value(item, 'id'))}><strong>{String(value(item, 'employeeCode'))}</strong><small>Gross {money(value(item, 'grossSalary'))}</small><small>Net {money(value(item, 'netSalary'))}</small><span className="badge">{String(value(item, 'paymentStatus'))}</span>{String(value(item, 'paymentStatus')).toUpperCase() === 'PENDING' && String(value(run, 'status')).toUpperCase() === 'LOCKED' && <div className="page-actions"><select className="filter" value={paymentMode} onChange={event => setPaymentMode(event.target.value)}><option>BANK_TRANSFER</option><option>UPI</option><option>CHEQUE</option><option>CASH</option></select><button className="primary" onClick={() => pay(item)} disabled={busy}>Pay {money(value(item, 'netSalary'))}</button></div>}</article>)}</div>{String(value(run, 'status')).toUpperCase() === 'LOCKED' && <p className="muted">{pending.length} payment{pending.length === 1 ? '' : 's'} pending. Locked runs cannot be recalculated.</p>}</section>}
  </div>;
}

function downloadCsv(filename: string, rows: SalesRow[]) {
  if (!rows.length) return;
  const columns = Array.from(new Set(rows.flatMap(row => Object.keys(row))));
  const csv = [columns, ...rows.map(row => columns.map(column => String(row[column] ?? '').replaceAll('"', '""')))].map(row => row.map(cell => `"${cell}"`).join(',')).join('\n');
  const anchor = document.createElement('a'); anchor.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' })); anchor.download = filename; anchor.click();
}
