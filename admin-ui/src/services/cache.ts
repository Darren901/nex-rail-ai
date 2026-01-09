import request from '../utils/request';

export interface CacheStats {
  totalKeys: number;
  dailyQuotaKeys: number;
  monthlyNotifyKeys: number;
  tokenKeys: number;
  timetableKeys: number;
  fareKeys: number;
}

export const getCacheStats = () => {
  return request.get<any, CacheStats>('/admin/cache/stats');
};

export const clearCache = (type: string) => {
  return request.delete<any, { message: string }>(`/admin/cache/clear/${type}`);
};
