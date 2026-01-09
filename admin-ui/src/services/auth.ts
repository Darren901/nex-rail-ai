import request from '../utils/request';

export interface LoginParams {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
}

export const login = (data: LoginParams) => {
  return request.post<any, LoginResponse>('/auth/login', data);
};
