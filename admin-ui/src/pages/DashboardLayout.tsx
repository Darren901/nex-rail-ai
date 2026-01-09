import React from 'react';
import { Layout, Menu, Button, theme } from 'antd';
import { 
  UserOutlined, 
  DashboardOutlined, 
  LogoutOutlined,
  ScheduleOutlined
} from '@ant-design/icons';
import { useNavigate, Outlet } from 'react-router-dom';

const { Header, Sider, Content } = Layout;

const DashboardLayout: React.FC = () => {
  const navigate = useNavigate();
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();

  const handleLogout = () => {
    localStorage.removeItem('token');
    navigate('/login');
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider collapsible breakpoint="lg">
        <div style={{ height: 32, margin: 16, background: 'rgba(255, 255, 255, 0.2)', textAlign: 'center', color: '#fff', lineHeight: '32px', fontWeight: 'bold' }}>
          NexRail Admin
        </div>
        <Menu
          theme="dark"
          mode="inline"
          defaultSelectedKeys={['1']}
          items={[
            {
              key: '1',
              icon: <DashboardOutlined />,
              label: '總覽',
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
          ]}
        />
      </Sider>
      <Layout>
        <Header style={{ padding: '0 24px', background: colorBgContainer, display: 'flex', justifyContent: 'flex-end', alignItems: 'center' }}>
          <Button type="text" icon={<LogoutOutlined />} onClick={handleLogout}>
            登出
          </Button>
        </Header>
        <Content style={{ margin: '24px 16px 0' }}>
          <div
            style={{
              padding: 24,
              minHeight: 360,
              background: colorBgContainer,
              borderRadius: borderRadiusLG,
            }}
          >
            {/* 這裡會渲染子路由的內容 */}
            <Outlet /> 
          </div>
        </Content>
      </Layout>
    </Layout>
  );
};

export default DashboardLayout;
