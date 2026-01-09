import axios from 'axios';
import { message } from 'antd';

const request = axios.create({
  baseURL: '/api',
  timeout: 5000,
});

// Request Interceptor: 自動帶上 Token
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response Interceptor: 統一處理錯誤
request.interceptors.response.use(
  (response) => {
    return response.data;
  },
  (error) => {
    if (error.response) {
      const { status } = error.response;
      if (status === 401 || status === 403) {
        message.error('登入已過期，請重新登入');
        localStorage.removeItem('token');
        window.location.href = '/login';
      } else {
        message.error(error.response.data?.message || '系統發生錯誤');
      }
    } else {
      message.error('網路連線異常');
    }
    return Promise.reject(error);
  }
);

export default request;
