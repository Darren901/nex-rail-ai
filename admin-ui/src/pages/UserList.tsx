import React, { useEffect, useState } from 'react';
import { Table, Avatar, Tag, Button, Space, Modal, Form, InputNumber, Select, message, Tooltip, Input, Progress } from 'antd';
import type { TableProps } from 'antd';
import { UserOutlined, EditOutlined, StopOutlined, CheckCircleOutlined, SearchOutlined } from '@ant-design/icons';
import { getUsers, updateUserStatus, updateUserQuota } from '../services/user';
import type { UserDetail, UpdateQuotaParams } from '../services/user';

const { Search } = Input;
type ColumnsType<T> = TableProps<T>['columns'];

const UserList: React.FC = () => {
  const [users, setUsers] = useState<UserDetail[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  
  // Modal 狀態
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserDetail | null>(null);
  const [form] = Form.useForm();

  const fetchUsers = async (page: number, searchKey?: string) => {
    setLoading(true);
    try {
      const res = await getUsers(page - 1, 10, searchKey);
      setUsers(res.content);
      setTotal(res.totalElements);
      setCurrentPage(page);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers(1, keyword);
  }, []);

  const onSearch = (value: string) => {
    setKeyword(value);
    fetchUsers(1, value);
  };

  const handleStatusChange = async (user: UserDetail) => {
    const newStatus = user.status === 'ENABLE' ? 'DISABLE' : 'ENABLE';
    try {
      await updateUserStatus(user.lineUserId, newStatus);
      message.success(`已${newStatus === 'ENABLE' ? '啟用' : '停用'}使用者`);
      fetchUsers(currentPage, keyword);
    } catch (error) {
      console.error(error);
    }
  };

  const handleQuotaEdit = (user: UserDetail) => {
    setEditingUser(user);
    setIsModalOpen(true);
    form.resetFields();
  };

  const handleQuotaSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (!editingUser) return;

      const payload: UpdateQuotaParams = {
        type: values.type,
        action: values.action,
        amount: values.amount
      };

      await updateUserQuota(editingUser.lineUserId, payload);
      message.success('額度已更新');
      setIsModalOpen(false);
      fetchUsers(currentPage, keyword);
    } catch (error) {
      console.error(error);
    }
  };

  const columns: ColumnsType<UserDetail> = [
    {
      title: '使用者',
      key: 'user',
      render: (_, record) => (
        <Space>
          <Avatar src={record.pictureUrl} icon={<UserOutlined />} />
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <span style={{ fontWeight: 'bold' }}>{record.displayName || '未知用戶'}</span>
            <code style={{ fontSize: '10px', color: '#aaa' }}>{record.lineUserId}</code>
          </div>
        </Space>
      ),
    },
    {
      title: '狀態',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status) => (
        <Tag color={status === 'ENABLE' ? 'success' : 'error'}>
          {status === 'ENABLE' ? '正常' : '停用'}
        </Tag>
      ),
    },
    {
      title: '今日對話額度',
      dataIndex: 'dailyQuota',
      key: 'dailyQuota',
      width: 180,
      render: (val) => (
        <div style={{ width: 120 }}>
          <Progress 
            percent={(val / 10) * 100} 
            size="small" 
            format={() => `${val}/10`} 
            status={val === 0 ? 'exception' : 'active'}
          />
        </div>
      ),
    },
    {
      title: '每月推播額度',
      dataIndex: 'monthlyQuota',
      key: 'monthlyQuota',
      width: 180,
      render: (val) => (
        <div style={{ width: 120 }}>
          <Progress 
            percent={(val / 5) * 100} 
            size="small" 
            strokeColor="#722ed1"
            format={() => `${val}/5`} 
            status={val === 0 ? 'exception' : 'active'}
          />
        </div>
      ),
    },
    {
      title: '最後活躍',
      dataIndex: 'lastActiveAt',
      key: 'lastActiveAt',
      render: (text) => text ? new Date(text).toLocaleString() : '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_, record) => (
        <Space size="middle">
          <Tooltip title="調整額度">
            <Button 
              type="primary" 
              shape="circle" 
              icon={<EditOutlined />} 
              onClick={() => handleQuotaEdit(record)} 
            />
          </Tooltip>
          <Tooltip title={record.status === 'ENABLE' ? "停用帳號" : "啟用帳號"}>
            <Button 
              danger={record.status === 'ENABLE'}
              type={record.status === 'ENABLE' ? "default" : "primary"}
              style={record.status === 'DISABLE' ? { backgroundColor: '#52c41a' } : {}}
              shape="circle" 
              icon={record.status === 'ENABLE' ? <StopOutlined /> : <CheckCircleOutlined />} 
              onClick={() => handleStatusChange(record)}
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2>使用者管理</h2>
        <Space>
          <Search
            placeholder="搜尋姓名或 Line ID"
            allowClear
            onSearch={onSearch}
            style={{ width: 300 }}
            enterButton
          />
          <Button icon={<SearchOutlined />} onClick={() => fetchUsers(currentPage, keyword)}>重新整理</Button>
        </Space>
      </div>
      
      <Table 
        columns={columns} 
        dataSource={users} 
        rowKey="lineUserId"
        loading={loading}
        scroll={{ x: 800 }} // 響應式滾動
        pagination={{
          current: currentPage,
          pageSize: 10,
          total: total,
          onChange: (page) => fetchUsers(page, keyword),
        }}
      />

      <Modal
        title={`調整額度 - ${editingUser?.displayName}`}
        open={isModalOpen}
        onOk={handleQuotaSubmit}
        onCancel={() => setIsModalOpen(false)}
      >
        <Form form={form} layout="vertical" initialValues={{ type: 'DAILY', action: 'ADD', amount: 10 }}>
          <Form.Item name="type" label="額度類型">
            <Select>
              <Select.Option value="DAILY">每日對話額度</Select.Option>
              <Select.Option value="MONTHLY">每月推播額度</Select.Option>
            </Select>
          </Form.Item>
          
          <Form.Item name="action" label="操作方式">
            <Select>
              <Select.Option value="ADD">增加/減少 (輸入負數為減少)</Select.Option>
              <Select.Option value="SET">直接設定 (覆蓋)</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item name="amount" label="數值" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default UserList;

