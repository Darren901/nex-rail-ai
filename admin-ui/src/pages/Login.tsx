import React, { useState } from 'react';
import { Form, Input, Button, Typography, message, Row, Col, Avatar } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { login } from '../services/auth';
import type { LoginParams } from '../services/auth';

const { Title, Text } = Typography;

const Login: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const onFinish = async (values: LoginParams) => {
    setLoading(true);
    try {
      const res = await login(values);
      message.success('登入成功');
      localStorage.setItem('token', res.token);
      navigate('/'); 
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const gradientBg = 'linear-gradient(135deg, #001f3f 0%, #0074D9 50%, #7FDBFF 100%)';

  return (
    <Row style={{ height: '100vh' }}>
      {/* 左側品牌區 - 手機版隱藏 */}
      <Col xs={0} md={12} lg={14} style={{ background: gradientBg, display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', color: '#fff', padding: 40 }}>
        <div style={{ textAlign: 'center', marginBottom: 40 }}>
          <Avatar 
            size={100} 
            style={{
              backgroundColor: 'rgba(255,255,255,0.2)',
              border: '4px solid rgba(255,255,255,0.4)',
              fontSize: 48,
              marginBottom: 24
            }}
            src="/src/assets/logo.png"
          >
          </Avatar>
          <Title level={1} style={{ color: '#fff', margin: 0 }}>NexRail AI</Title>
          <Text style={{ color: 'rgba(255,255,255,0.8)', fontSize: 18, marginTop: 16, display: 'block' }}>
            您的智慧高鐵行程助理
          </Text>
        </div>
        <div style={{ position: 'absolute', bottom: 40, opacity: 0.6 }}>
          © 2026 NexRail AI Admin System
        </div>
      </Col>

      {/* 右側表單區 */}
      <Col xs={24} md={12} lg={10} style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', background: '#fff' }}>
        <div style={{ width: '100%', maxWidth: 400, padding: 24 }}>
          <div style={{ marginBottom: 40, textAlign: 'center' }}>
            <Title level={2} style={{ color: '#001f3f' }}>歡迎回來</Title>
            <Text type="secondary">請輸入您的管理員帳號密碼</Text>
          </div>

          <Form
            name="login"
            initialValues={{ remember: true }}
            onFinish={onFinish}
            size="large"
            layout="vertical"
          >
            <Form.Item
              name="username"
              rules={[{ required: true, message: '請輸入帳號!' }]
            }>
              <Input prefix={<UserOutlined style={{ color: '#0074D9' }} />} placeholder="帳號" />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[{ required: true, message: '請輸入密碼!' }]
            }>
              <Input.Password prefix={<LockOutlined style={{ color: '#0074D9' }} />} placeholder="密碼" />
            </Form.Item>

            <Form.Item style={{ marginTop: 24 }}>
              <Button 
                type="primary" 
                htmlType="submit" 
                style={{
                  width: '100%', 
                  height: 48, 
                  background: gradientBg,
                  border: 'none',
                  fontSize: 16,
                  fontWeight: 'bold'
                }} 
                loading={loading}
              >
                登入系統
              </Button>
            </Form.Item>
          </Form>
        </div>
      </Col>
    </Row>
  );
};

export default Login;

