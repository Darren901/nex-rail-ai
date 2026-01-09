import React, { useEffect, useState } from 'react';
import { Table, Avatar, Tag, Button, Space, Modal, Form, InputNumber, Select, message, Tooltip } from 'antd';
import type { TableProps } from 'antd'; // Use TableProps instead of direct es path
import { UserOutlined, EditOutlined, StopOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { getUsers, updateUserStatus, updateUserQuota } from '../services/user';
import type { UserDetail, UpdateQuotaParams } from '../services/user';

type ColumnsType<T> = TableProps<T>['columns'];

const UserList: React.FC = () => {
  const [users, setUsers] = useState<UserDetail[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  
  // Modal 狀態
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserDetail | null>(null);
  const [form] = Form.useForm();

  const fetchUsers = async (page: number) => {
    setLoading(true);
    try {
      // API page 是 0-based
      const res = await getUsers(page - 1, 10);
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
    fetchUsers(1);
  }, []);

  const handleStatusChange = async (user: UserDetail) => {
    const newStatus = user.status === 'ENABLE' ? 'DISABLE' : 'ENABLE';
    try {
      await updateUserStatus(user.lineUserId, newStatus);
      message.success(`已${newStatus === 'ENABLE' ? '啟用' : '停用'}使用者`);
      fetchUsers(currentPage);
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
      fetchUsers(currentPage);
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
            <span style={{ fontSize: '12px', color: '#888' }}>{record.lineUserId}</span>
          </div>
        </Space>
      ),
    },
    {
      title: '狀態',
      dataIndex: 'status',
      key: 'status',
      render: (status) => (
        <Tag color={status === 'ENABLE' ? 'success' : 'error'}>
          {status === 'ENABLE' ? '正常' : '停用'}
        </Tag>
      ),
    },
    {
      title: '每日額度 (剩餘)',
      dataIndex: 'dailyQuota',
      key: 'dailyQuota',
      render: (val) => <Tag color="blue">{val} / 10</Tag>,
    },
    {
      title: '每月額度 (剩餘)',
      dataIndex: 'monthlyQuota',
      key: 'monthlyQuota',
      render: (val) => <Tag color="purple">{val} / 5</Tag>,
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
              type="default" // 若要顯示綠色啟用按鈕，可用 style 或 class
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
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <h2>使用者管理</h2>
        <Button onClick={() => fetchUsers(currentPage)}>重新整理</Button>
      </div>
      
      <Table 
        columns={columns} 
        dataSource={users} 
        rowKey="lineUserId"
        loading={loading}
        pagination={{
          current: currentPage,
          pageSize: 10,
          total: total,
          onChange: (page) => fetchUsers(page),
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
