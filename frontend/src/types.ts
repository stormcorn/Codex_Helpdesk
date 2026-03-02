export type Role = 'ADMIN' | 'IT' | 'USER';

export type AuthMode = 'login' | 'register';
export type DashboardTab = 'helpdesk' | 'itdesk' | 'archive' | 'members' | 'account';
export type FeedbackType = 'success' | 'error' | '';
export type SortOrder = 'newest' | 'oldest';

export type TicketPriority = 'GENERAL' | 'URGENT';
export type TicketStatus = 'OPEN' | 'PROCEEDING' | 'PENDING' | 'CLOSED' | 'DELETED';
export type TicketActiveStatusFilter = 'ALL' | 'OPEN' | 'PROCEEDING' | 'PENDING';
export type TicketArchiveStatusFilter = 'ALL' | 'CLOSED' | 'DELETED';
export type NumberBooleanMap = Record<number, boolean>;
export type NumberStringMap = Record<number, string>;
export type NumberTicketStatusMap = Record<number, TicketStatus>;
export type TicketStats = {
  total: number;
  open: number;
  proceeding: number;
  pending: number;
  closed: number;
  deleted: number;
  todayNew: number;
};
export type DashboardContext = {
  dashboardTab: DashboardTab;
  isItOrAdmin: boolean;
  isAdmin: boolean;
  notificationsOpen: boolean;
  unreadCount: number;
};

export type Member = {
  id: number;
  employeeId: string;
  name: string;
  email: string;
  role: Role;
  createdAt: string;
};

export type LoginForm = {
  employeeId: string;
  password: string;
};

export type RegisterForm = {
  employeeId: string;
  name: string;
  email: string;
  password: string;
  groupId: number | null;
};

export type Attachment = {
  id: number;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
};

export type TicketMessage = {
  id: number;
  content: string;
  authorEmployeeId: string;
  authorName: string;
  authorRole: Role;
  createdAt: string;
};

export type TicketStatusHistory = {
  id: number;
  fromStatus: string | null;
  toStatus: string;
  changedByMemberId: number | null;
  changedByEmployeeId: string;
  changedByName: string;
  changedByRole: string;
  createdAt: string;
};

export type Ticket = {
  id: number;
  name: string;
  email: string;
  subject: string;
  description: string;
  status: TicketStatus;
  priority: TicketPriority;
  supervisorApproved: boolean;
  supervisorApprovedByMemberId: number | null;
  supervisorApprovedAt: string | null;
  groupId: number | null;
  groupName: string | null;
  categoryId: number | null;
  categoryName: string | null;
  createdByMemberId: number | null;
  createdByEmployeeId: string | null;
  deleted: boolean;
  deletedAt: string | null;
  createdAt: string;
  attachments: Attachment[];
  messages: TicketMessage[];
  statusHistories: TicketStatusHistory[];
};

export type TicketForm = {
  name: string;
  email: string;
  subject: string;
  description: string;
  priority: TicketPriority;
  groupId: number | null;
  categoryId: number | null;
};

export type MyGroup = {
  id: number;
  name: string;
  supervisor: boolean;
};

export type PublicGroupOption = {
  id: number;
  name: string;
};

export type HelpdeskCategory = {
  id: number;
  name: string;
  createdAt: string;
};

export type AdminGroupMember = {
  memberId: number;
  employeeId: string;
  name: string;
  role: Role;
  supervisor: boolean;
};

export type AdminGroup = {
  id: number;
  name: string;
  createdAt: string;
  members: AdminGroupMember[];
};

export type NotificationItem = {
  id: number;
  type: 'TICKET_CREATED' | 'TICKET_REPLY' | 'TICKET_STATUS';
  message: string;
  ticketId: number | null;
  read: boolean;
  createdAt: string;
};

export type NotificationListResponse = {
  notifications: NotificationItem[];
  unreadCount: number;
};

export type AuditLogItem = {
  id: number;
  actorMemberId: number | null;
  actorEmployeeId: string;
  actorName: string;
  actorRole: string;
  action: string;
  entityType: string;
  entityId: number | null;
  beforeJson: string | null;
  afterJson: string | null;
  metadataJson: string | null;
  createdAt: string;
};
