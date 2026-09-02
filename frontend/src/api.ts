const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1';
export type User = { id: string; fullName: string; email: string; roles: string[] };
export type AuthResult = { accessToken: string; refreshToken: string; user: User };
export type Lead = { id: string; leadNumber: string; customerName: string; mobile: string; source: string; status: string; temperature: string; assignedTo: string | null; createdAt: string };
export type Page<T> = { content: T[]; totalElements: number; totalPages: number; number: number };
export type Dashboard = { role: string; title: string; subtitle: string; metrics: { label: string; value: string; note: string; money: boolean }[]; queue: { label: string; count: number; note: string }[]; modules: string[]; features: string[] };
export type Workspace = { module: string; title: string; role: string; rows: Record<string, string | number | null>[] };
export type WorkspaceRecord = { id: string; module: string; title: string; status: string; details: string | null; createdAt: string; updatedAt: string };
export type InventoryProject = { id: string; code: string; name: string; city: string | null; address: string | null; status: string };
export type InventoryUnit = { id: string; projectId: string; projectCode: string; projectName: string; wing: string; floor: string; unitNumber: string; configuration: string; carpetArea: number; builtUpArea: number; basePrice: number; facing: string | null; parking: string | null; amenities: string | null; status: string; reservedUntil: string | null };
export type SalesRow = Record<string, string | number | boolean | null>;

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('sv_access_token');
  const response = await fetch(`${API_URL}${path}`, { ...options, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...options.headers } });
  if (!response.ok) { const error = await response.json().catch(() => ({})); throw new Error(error.message ?? 'Something went wrong.'); }
  return response.json() as Promise<T>;
}
export const api = {
  login: (email: string, password: string) => request<AuthResult>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
  me: () => request<User>('/auth/me'),
  dashboard: () => request<Dashboard>('/dashboard'),
  workspace: (module: string) => request<Workspace>(`/workspace/${module}`),
  leads: () => request<Page<Lead>>('/leads?size=50'),
  createLead: (lead: Record<string, unknown>) => request<Lead>('/leads', { method: 'POST', body: JSON.stringify(lead) }),
  records: (module: string) => request<WorkspaceRecord[]>(`/workspace/${module}/records`),
  createRecord: (module: string, record: { title: string; status: string; details: string }) => request<WorkspaceRecord>(`/workspace/${module}/records`, { method: 'POST', body: JSON.stringify(record) })
  ,inventory: (filters: { projectId?: string; status?: string; wing?: string; configuration?: string } = {}) => { const query = new URLSearchParams(Object.entries(filters).filter(([, value]) => Boolean(value)) as [string,string][]); return request<InventoryUnit[]>(`/inventory${query.toString() ? `?${query.toString()}` : ''}`); }
  ,inventoryProjects: () => request<InventoryProject[]>('/inventory/projects')
  ,createProject: (project: Record<string, unknown>) => request<InventoryProject>('/inventory/projects', { method: 'POST', body: JSON.stringify(project) })
  ,updateProject: (id: string, project: Record<string, unknown>) => request<InventoryProject>(`/inventory/projects/${id}`, { method: 'PUT', body: JSON.stringify(project) })
  ,createUnit: (unit: Record<string, unknown>) => request<InventoryUnit>('/inventory/units', { method: 'POST', body: JSON.stringify(unit) })
  ,updatePrice: (unitId: string, price: number, reason: string) => request<InventoryUnit>(`/inventory/units/${unitId}/price`, { method: 'PUT', body: JSON.stringify({ price, reason }) })
  ,reserveUnit: (unitId: string, hours = 48) => request<InventoryUnit>(`/inventory/units/${unitId}/reserve`, { method: 'POST', body: JSON.stringify({ hours }) })
  ,releaseUnit: (unitId: string) => request<InventoryUnit>(`/inventory/units/${unitId}/release`, { method: 'POST' })
  ,qualification: (leadId: string) => request<SalesRow>(`/sales/leads/${leadId}/qualification`)
  ,qualify: (leadId: string, data: Record<string, unknown>) => request<SalesRow>(`/sales/leads/${leadId}/qualification`, { method: 'PATCH', body: JSON.stringify(data) })
  ,salesFollowUps: (overdue = false) => request<SalesRow[]>(`/sales/follow-ups?overdue=${overdue}`)
  ,salesExecutives: () => request<SalesRow[]>('/sales/executives')
  ,salesSiteVisits: () => request<SalesRow[]>('/sales/site-visits')
  ,createSiteVisit: (leadId: string, data: Record<string, unknown>) => request<SalesRow>(`/sales/leads/${leadId}/site-visits`, { method: 'POST', body: JSON.stringify(data) })
  ,updateSiteVisit: (visitId: string, data: Record<string, unknown>) => request<SalesRow>(`/sales/site-visits/${visitId}`, { method: 'PATCH', body: JSON.stringify(data) })
  ,salesNegotiations: () => request<SalesRow[]>('/sales/negotiations')
  ,createNegotiation: (leadId: string, data: Record<string, unknown>) => request<SalesRow>(`/sales/leads/${leadId}/negotiations`, { method: 'POST', body: JSON.stringify(data) })
  ,approveNegotiation: (id: string, approve: boolean, comment: string) => request<SalesRow>(`/sales/negotiations/${id}/approve`, { method: 'POST', body: JSON.stringify({ approve, comment }) })
  ,salesBookings: () => request<SalesRow[]>('/sales/bookings')
  ,createFollowUp: (leadId: string, data: Record<string, unknown>) => request<SalesRow>(`/sales/leads/${leadId}/follow-ups`, { method: 'POST', body: JSON.stringify(data) })
  ,leadQuotations: (leadId: string) => request<SalesRow[]>(`/sales/leads/${leadId}/quotations`)
  ,createQuotation: (leadId: string, data: Record<string, unknown>) => request<SalesRow>(`/sales/leads/${leadId}/quotations`, { method: 'POST', body: JSON.stringify(data) })
  ,approveQuotation: (id: string, approve: boolean, comment: string) => request<SalesRow>(`/sales/quotations/${id}/approve`, { method: 'POST', body: JSON.stringify({ approve, comment }) })
  ,reviseQuotation: (id: string, data: Record<string, unknown>) => request<SalesRow>(`/sales/quotations/${id}/revision`, { method: 'POST', body: JSON.stringify(data) })
  ,transferLead: (leadId: string, assigneeId: string, reason: string) => request<SalesRow>(`/sales/leads/${leadId}/transfer`, { method: 'POST', body: JSON.stringify({ assigneeId, reason }) })
  ,createBooking: (leadId: string, data: Record<string, unknown>) => request<SalesRow>(`/sales/leads/${leadId}/bookings`, { method: 'POST', body: JSON.stringify(data) })
  ,lifecycleBookings: () => request<SalesRow[]>('/lifecycle/bookings')
  ,lifecycleSummary: (bookingId: string) => request<Record<string, unknown>>(`/lifecycle/bookings/${bookingId}/summary`)
  ,uploadDocument: (bookingId: string, data: Record<string, unknown>) => request<SalesRow>(`/lifecycle/bookings/${bookingId}/documents`, { method: 'POST', body: JSON.stringify(data) })
  ,uploadDocumentContent: async (documentId: string, file: File) => { const form = new FormData(); form.append('file', file); const response = await fetch(`${API_URL}/lifecycle/documents/${documentId}/content`, { method: 'POST', headers: { Authorization: `Bearer ${localStorage.getItem('sv_access_token') ?? ''}` }, body: form }); if (!response.ok) throw new Error('Document upload failed.'); return response.json() as Promise<SalesRow>; }
  ,portalAccess: (email: string, bookingNumber?: string) => request<{ portalToken: string; expiresAt: string; customer: string }>('/portal/access', { method: 'POST', body: JSON.stringify({ email, bookingNumber: bookingNumber || null }) })
  ,portalMe: (token: string) => request<Record<string, unknown>>('/portal/me', { headers: { 'X-Portal-Token': token } })
  ,portalTicket: (token: string, data: Record<string, unknown>) => request<SalesRow>('/portal/tickets', { method: 'POST', headers: { 'X-Portal-Token': token }, body: JSON.stringify(data) })
  ,verifyDocument: (documentId: string, status: string, reason = '') => request<SalesRow>(`/lifecycle/documents/${documentId}/verify`, { method: 'PATCH', body: JSON.stringify({ status, reason }) })
  ,updateLoan: (bookingId: string, data: Record<string, unknown>) => request<SalesRow>(`/lifecycle/bookings/${bookingId}/loan`, { method: 'PUT', body: JSON.stringify(data) })
  ,updateAgreement: (bookingId: string, data: Record<string, unknown>) => request<SalesRow>(`/lifecycle/bookings/${bookingId}/agreement`, { method: 'PUT', body: JSON.stringify(data) })
  ,updatePossessionChecklist: (possessionId: string, itemCode: string, completed: boolean, remarks: string) => request<SalesRow>(`/lifecycle/possession/${possessionId}/checklist/${itemCode}`, { method: 'PATCH', body: JSON.stringify({ completed, remarks }) })
  ,updatePossessionStatus: (possessionId: string, status: string, signoffName = '') => request<SalesRow>(`/lifecycle/possession/${possessionId}/status`, { method: 'PATCH', body: JSON.stringify({ status, signoffName }) })
  ,createPossession: (bookingId: string, scheduledDate: string) => request<SalesRow>(`/lifecycle/bookings/${bookingId}/possession`, { method: 'POST', body: JSON.stringify({ scheduledDate }) })
  ,financeDashboard: () => request<Record<string, unknown>>('/finance/dashboard')
  ,financeBookings: () => request<SalesRow[]>('/finance/bookings')
  ,financeReceivables: () => request<SalesRow[]>('/finance/receivables')
  ,financePayments: () => request<SalesRow[]>('/finance/payments')
  ,financeInstallments: (bookingId: string) => request<SalesRow[]>(`/finance/bookings/${bookingId}/installments`)
  ,createFinancePayment: (bookingId: string, data: Record<string, unknown>) => request<SalesRow>(`/finance/bookings/${bookingId}/payments`, { method: 'POST', body: JSON.stringify(data) })
  ,reverseFinancePayment: (paymentId: string, reason: string) => request<SalesRow>(`/finance/payments/${paymentId}/reverse`, { method: 'POST', body: JSON.stringify({ reason }) })
  ,hrDashboard: () => request<Record<string, unknown>>('/hr/dashboard')
  ,hrEmployees: () => request<SalesRow[]>('/hr/employees')
  ,hrAttendance: () => request<SalesRow[]>('/hr/attendance')
  ,hrLeave: () => request<SalesRow[]>('/hr/leave')
  ,hrPayrollRuns: () => request<SalesRow[]>('/hr/payroll-runs')
  ,hrPayrollItems: (runId: string) => request<SalesRow[]>(`/hr/payroll-runs/${runId}/items`)
  ,createPayrollRun: (runMonth: string) => request<SalesRow>('/hr/payroll-runs', { method: 'POST', body: JSON.stringify({ runMonth }) })
  ,finalizePayroll: (runId: string) => request<SalesRow>(`/hr/payroll-runs/${runId}/finalize`, { method: 'POST' })
  ,procurementDashboard: () => request<Record<string, unknown>>('/procurement/dashboard')
  ,procurementVendors: () => request<SalesRow[]>('/procurement/vendors')
  ,purchaseOrders: () => request<SalesRow[]>('/procurement/purchase-orders')
  ,vendorBills: () => request<SalesRow[]>('/procurement/bills')
  ,pettyCash: () => request<SalesRow[]>('/procurement/petty-cash')
  ,supportDashboard: () => request<Record<string, unknown>>('/support/dashboard')
  ,supportTickets: () => request<SalesRow[]>('/support/tickets')
  ,supportMaintenance: () => request<SalesRow[]>('/support/maintenance')
  ,supportReferrals: () => request<SalesRow[]>('/support/referrals')
  ,createSupportTicket: (data: Record<string, unknown>) => request<SalesRow>('/support/tickets', { method: 'POST', body: JSON.stringify(data) })
  ,updateSupportStatus: (id: string, data: Record<string, unknown>) => request<SalesRow>(`/support/tickets/${id}/status`, { method: 'PATCH', body: JSON.stringify(data) })
  ,updateMaintenanceStatus: (id: string, data: Record<string, unknown>) => request<SalesRow>(`/support/maintenance/${id}/status`, { method: 'PATCH', body: JSON.stringify(data) })
  ,notifications: (unreadOnly = false) => request<SalesRow[]>(`/notifications?unreadOnly=${unreadOnly}`)
  ,notificationUnreadCount: () => request<{ unread: number }>('/notifications/unread-count')
  ,markNotificationRead: (id: string) => request<SalesRow>(`/notifications/${id}/read`, { method: 'PATCH' })
  ,markAllNotificationsRead: () => request<{ updated: number }>('/notifications/read-all', { method: 'POST' })
  ,reportCatalog: () => request<SalesRow[]>('/reports/catalog')
  ,reportData: (key: string) => request<{ reportKey: string; generatedAt: string; rows: SalesRow[]; rowCount: number }>(`/reports/${key}/data`)
  ,reportExportUrl: (key: string, format: 'csv' | 'excel' | 'pdf') => `${API_URL}/reports/${key}/export?format=${format}`
  ,reportExport: async (key: string, format: 'csv' | 'excel' | 'pdf') => { const response = await fetch(`${API_URL}/reports/${key}/export?format=${format}`, { headers: { Authorization: `Bearer ${localStorage.getItem('sv_access_token') ?? ''}` } }); if (!response.ok) throw new Error('Report export failed.'); return response.blob(); }
};
