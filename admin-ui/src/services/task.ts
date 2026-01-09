import request from '../utils/request';
import type { PageResponse } from './user';

export interface ScheduleTask {
  id: number;
  userId: string;
  triggerTime: string;
  content: string;
  taskType: 'REMINDER' | 'TICKET_MONITOR';
  payload: string;
  status: 'PENDING' | 'EXECUTED' | 'COMPLETED' | 'CANCELLED' | 'FAILED' | 'EXPIRED';
  createdAt: string;
}

export const getTasks = (status: string, page: number, size: number) => {
  return request.get<any, PageResponse<ScheduleTask>>('/admin/tasks', {
    params: { status, page, size },
  });
};

export const cancelTask = (taskId: number) => {
  return request.delete(`/admin/tasks/${taskId}`);
};
