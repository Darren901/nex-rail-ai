import React, { useState } from 'react';
import { Card, Form, Input, Button, Typography, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { login } from '../services/auth';
import type { LoginParams } from '../services/auth';

const { Title } = Typography;

const Login: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const onFinish = async (values: LoginParams) => {
    setLoading(true);
    try {
      const res = await login(values);
      message.success('登入成功');
      localStorage.setItem('token', res.token);
      navigate('/'); // 跳轉到首頁
    } catch (error) {
      console.error(error);
      // 錯誤訊息已經由 interceptor 處理了，這裡不用再報錯
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ 
      display: 'flex', 
      justifyContent: 'center', 
      alignItems: 'center', 
      height: '100vh', 
      background: '#f0f2f5' 
    }}>
      <Card style={{ width: 400, boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <Title level={3}>NexRail AI 後台管理</Title>
        </div>
        
        <Form
          name="login"
          initialValues={{ remember: true }}
          onFinish={onFinish}
          size="large"
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '請輸入帳號!' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="帳號 (admin)" />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: '請輸入密碼!' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密碼 (password)" />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" style={{ width: '100%' }} loading={loading}>
              登入
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default Login;
