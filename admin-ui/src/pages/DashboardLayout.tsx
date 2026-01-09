import React, { useState } from 'react';
import {Layout, Menu, Button, theme, Typography, Avatar, Space} from 'antd';
import { 
  UserOutlined, 
  DashboardOutlined, 
  LogoutOutlined,
  ScheduleOutlined,
  DatabaseOutlined
} from '@ant-design/icons';
import { useNavigate, Outlet, useLocation } from 'react-router-dom';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const DashboardLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();

  const handleLogout = () => {
    localStorage.removeItem('token');
    navigate('/login');
  };

  const getSelectedKey = () => {
    const path = location.pathname;
    if (path === '/') return '1';
    if (path === '/users') return '2';
    if (path === '/tasks') return '3';
    if (path === '/cache') return '4';
    return '1';
  };

  const gradientBg = 'linear-gradient(135deg, #001f3f 0%, #0074D9 50%, #7FDBFF 100%)';

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider 
        collapsible 
        collapsed={collapsed}
        onCollapse={(value) => setCollapsed(value)}
        breakpoint="lg" 
        width={250}
        style={{ 
          background: gradientBg, // 側邊欄使用漸層
          boxShadow: '2px 0 8px 0 rgba(29,35,41,.05)' 
        }}
      >
        <div style={{ 
          height: 64, 
          display: 'flex', 
          alignItems: 'center', 
          justifyContent: 'center', 
          padding: '0 16px',
          borderBottom: '1px solid rgba(255,255,255,0.1)',
          marginBottom: 16,
          overflow: 'hidden' // 防止收合瞬間文字溢出
        }}>
          <Avatar
            shape="circle"
            size={32}
            style={{
              backgroundColor: '#0074D9',
              marginRight: collapsed ? 0 : 12, // 收合時移除右邊距
              minWidth: 32 // 防止 Avatar 被壓縮
            }}
            src="/src/assets/logo.png"
          >
          </Avatar>
          {!collapsed && (
            <Text strong style={{ fontSize: '20px', color: '#fff', letterSpacing: '1px', whiteSpace: 'nowrap' }}>
              NexRail AI
            </Text>
          )}
        </div>
        <Menu
          mode="inline"
          selectedKeys={[getSelectedKey()]}
          style={{ background: 'transparent', borderRight: 0 }}
          theme="dark" // 讓文字自動變白
          items={[
            {
              key: '1',
              icon: <DashboardOutlined />,
              label: '系統總覽',
              onClick: () => navigate('/')
            },
            {
              key: '2',
              icon: <UserOutlined />,
              label: '用戶管理',
              onClick: () => navigate('/users')
            },
            {
              key: '3',
              icon: <ScheduleOutlined />,
              label: '任務管理',
              onClick: () => navigate('/tasks')
            },
            {
              key: '4',
              icon: <DatabaseOutlined />,
              label: '系統快取',
              onClick: () => navigate('/cache')
            },
          ]}
        />
      </Sider>
      <Layout>
        <Header style={{ 
          padding: '0 24px', 
          background: colorBgContainer,
          display: 'flex', 
          justifyContent: 'flex-end', 
          alignItems: 'center',
          boxShadow: '0 1px 4px rgba(0,21,41,.08)',
          zIndex: 1,
          height: 64
        }}>
          <Space>
            <Avatar icon={<UserOutlined />} style={{ backgroundColor: '#0074D9' }} />
            <Text strong>管理員</Text>
            <Button type="text" icon={<LogoutOutlined />} onClick={handleLogout}>
              登出
            </Button>
          </Space>
        </Header>
        <Content style={{ margin: '24px 16px', padding: 24, background: 'transparent', overflow: 'initial' }}>
          <div style={{ background: colorBgContainer, padding: 24, borderRadius: borderRadiusLG, minHeight: '80vh' }}>
            <Outlet /> 
          </div>
        </Content>
      </Layout>
    </Layout>
  );
};

export default DashboardLayout;
