import React, { useEffect, useState } from 'react';
import { Table, Tag, Button, Space, Tabs, Popconfirm, message, Typography } from 'antd';
import type { TableProps } from 'antd';
import { ClockCircleOutlined, DeleteOutlined } from '@ant-design/icons';
import { getTasks, cancelTask } from '../services/task';
import type { ScheduleTask } from '../services/task';

const { Text } = Typography;
type ColumnsType<T> = TableProps<T>['columns'];

const TaskList: React.FC = () => {
  const [tasks, setTasks] = useState<ScheduleTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [status, setStatus] = useState('PENDING');

  const fetchTasks = async (targetStatus: string, page: number) => {
    setLoading(true);
    try {
      const res = await getTasks(targetStatus, page - 1, 10);
      setTasks(res.content);
      setTotal(res.totalElements);
      setCurrentPage(page);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTasks(status, 1);
  }, [status]);

  const handleCancel = async (taskId: number) => {
    try {
      await cancelTask(taskId);
      message.success('任務已取消');
      fetchTasks(status, currentPage);
    } catch (error) {
      console.error(error);
    }
  };

  const columns: ColumnsType<ScheduleTask> = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 80,
    },
    {
      title: '類型',
      dataIndex: 'taskType',
      key: 'taskType',
      render: (type) => (
        <Tag color={type === 'TICKET_MONITOR' ? 'orange' : 'blue'}>
          {type === 'TICKET_MONITOR' ? '搶票監控' : '待辦提醒'}
        </Tag>
      ),
    },
    {
      title: '內容',
      dataIndex: 'content',
      key: 'content',
      ellipsis: true,
      render: (text, record) => (
        <Space direction="vertical" size={0}>
          <Text strong style={{ fontSize: '13px' }}>{text}</Text>
          {record.taskType === 'TICKET_MONITOR' && (
            <code style={{ fontSize: '10px', color: '#888' }}>Payload: {record.payload.substring(0, 50)}...</code>
          )}
        </Space>
      )
    },
    {
      title: '觸發/發車時間',
      dataIndex: 'triggerTime',
      key: 'triggerTime',
      width: 180,
      render: (text) => (
        <Space>
          <ClockCircleOutlined style={{ color: '#888' }} />
          {new Date(text).toLocaleString()}
        </Space>
      ),
    },
    {
      title: '用戶 ID',
      dataIndex: 'userId',
      key: 'userId',
      width: 150,
      ellipsis: true,
    },
    {
      title: '狀態',
      dataIndex: 'status',
      key: 'status',
      render: (val) => {
        const colors: Record<string, string> = {
          PENDING: 'processing',
          COMPLETED: 'success',
          EXECUTED: 'cyan',
          FAILED: 'error',
          EXPIRED: 'default',
          CANCELLED: 'default',
        };
        return <Tag color={colors[val]}>{val}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        record.status === 'PENDING' && (
          <Popconfirm
            title="確定要取消此任務嗎？"
            onConfirm={() => handleCancel(record.id)}
            okText="是"
            cancelText="否"
          >
            <Button danger type="text" icon={<DeleteOutlined />}>取消</Button>
          </Popconfirm>
        )
      ),
    },
  ];

  const tabItems = [
    { key: 'PENDING', label: '進行中' },
    { key: 'COMPLETED', label: '已完成' },
    { key: 'EXECUTED', label: '已執行' },
    { key: 'FAILED', label: '失敗' },
    { key: 'EXPIRED', label: '已過期' },
    { key: 'CANCELLED', label: '已取消' },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <h2>任務管理</h2>
      </div>

      <Tabs 
        activeKey={status} 
        onChange={(key) => {
          setStatus(key);
          setCurrentPage(1);
        }}
        items={tabItems}
      />
      
      <Table 
        columns={columns} 
        dataSource={tasks} 
        rowKey="id"
        loading={loading}
        scroll={{ x: 1000 }} // 任務列表欄位較多，設定寬一點
        pagination={{
          current: currentPage,
          pageSize: 10,
          total: total,
          onChange: (page) => fetchTasks(status, page),
        }}
      />
    </div>
  );
};

export default TaskList;
