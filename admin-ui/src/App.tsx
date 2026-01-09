import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/Login';
import DashboardLayout from './pages/DashboardLayout';
import UserList from './pages/UserList';
import TaskList from './pages/TaskList';
import CacheManager from './pages/CacheManager';
import DashboardHome from './pages/DashboardHome';

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
          <Route index element={<DashboardHome />} />
          <Route path="users" element={<UserList />} />
          <Route path="tasks" element={<TaskList />} />
          <Route path="cache" element={<CacheManager />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
};

export default App;