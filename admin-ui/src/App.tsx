import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/Login';
import DashboardLayout from './pages/DashboardLayout';
import UserList from './pages/UserList';

// 一個簡單的路由保護元件
const PrivateRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const token = localStorage.getItem('token');
  return token ? <>{children}</> : <Navigate to="/login" replace />;
};

const App: React.FC = () => {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        
        {/* 保護所有後台頁面 */}
        <Route path="/" element={
          <PrivateRoute>
            <DashboardLayout />
          </PrivateRoute>
        }>
          {/* 預設首頁 */}
          <Route index element={<div><h2>👋 歡迎回來，管理員！</h2><p>請選擇左側選單進行操作。</p></div>} />
          <Route path="users" element={<UserList />} />
          <Route path="tasks" element={<div>任務管理頁面 (建置中)</div>} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
};

export default App;