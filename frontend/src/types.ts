export type Role = 'ADMIN' | 'IT' | 'USER';

export type DashboardTab = 'helpdesk' | 'itdesk' | 'members';
export type FeedbackType = 'success' | 'error' | '';

export type AuthMode = 'login' | 'register';

export type LoginForm = {
  employeeId: string;
  password: string;
};

export type RegisterForm = {
  employeeId: string;
  name: string;
  email: string;
  password: string;
};

export type Member = {
  id: number;
  employeeId: string;
  name: string;
  email: string;
  role: Role;
  createdAt: string;
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

export type TicketStatus = 'OPEN' | 'PROCEEDING' | 'PENDING' | 'CLOSED' | 'DELETED';

export type Ticket = {
  id: number;
  name: string;
  email: string;
  subject: string;
  description: string;
  status: TicketStatus;
  createdByMemberId: number | null;
  deleted: boolean;
  deletedAt: string | null;
  createdAt: string;
  attachments: Attachment[];
  messages: TicketMessage[];
};

export type TicketForm = {
  name: string;
  email: string;
  subject: string;
  description: string;
};

export type TicketStats = {
  total: number;
  todayNew: number;
  open: number;
  proceeding: number;
  pending: number;
  closed: number;
  deleted: number;
};

export type NotificationType = 'TICKET_CREATED' | 'TICKET_REPLY' | 'TICKET_STATUS';

export type NotificationItem = {
  id: number;
  type: NotificationType;
  message: string;
  ticketId: number | null;
  read: boolean;
  createdAt: string;
};
