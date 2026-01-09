import request from '../utils/request';

export interface UserDetail {
  lineUserId: string;
  displayName: string;
  pictureUrl: string;
  status: 'ENABLE' | 'DISABLE';
  joinedAt: string;
  lastActiveAt: string;
  dailyQuota: number;
  monthlyQuota: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface UpdateQuotaParams {
  type: 'DAILY' | 'MONTHLY';
  action: 'SET' | 'ADD';
  amount: number;
}

export const getUsers = (page: number, size: number, keyword?: string) => {
  return request.get<any, PageResponse<UserDetail>>('/admin/users', {
    params: { page, size, keyword },
  });
};

export const updateUserStatus = (userId: string, status: 'ENABLE' | 'DISABLE') => {
  return request.post(`/admin/users/${userId}/status`, { status });
};

export const updateUserQuota = (userId: string, data: UpdateQuotaParams) => {
  return request.post(`/admin/users/${userId}/quota`, data);
};
