import request from '../utils/request';

export interface DashboardStats {
  totalUsers: number;
  activeTasks: number;
  todayMessages: number;
  systemUptime: string;
}

export interface LineUsage {
  type: string;
  totalQuota: number;
  totalUsage: number;
}

export const getDashboardStats = () => {
  return request.get<any, DashboardStats>('/admin/dashboard/stats');
};

export const getLineUsage = () => {
  return request.get<any, LineUsage>('/admin/dashboard/line-usage');
};
