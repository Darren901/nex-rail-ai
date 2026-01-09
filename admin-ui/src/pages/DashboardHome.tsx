import React, { useEffect, useState } from 'react';
import { Card, Col, Row, Statistic, Progress, Spin } from 'antd';
import { UserOutlined, ScheduleOutlined, FieldTimeOutlined, MessageOutlined } from '@ant-design/icons';
import { getDashboardStats, getLineUsage, type DashboardStats, type LineUsage } from '../services/dashboard';

const DashboardHome: React.FC = () => {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [lineUsage, setLineUsage] = useState<LineUsage | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [statsData, usageData] = await Promise.all([
          getDashboardStats(),
          getLineUsage().catch(() => null) // LINE API 失敗不影響其他
        ]);
        setStats(statsData);
        setLineUsage(usageData);
      } catch (error) {
        console.error(error);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  if (loading) {
    return <div style={{ textAlign: 'center', marginTop: 50 }}><Spin size="large" /></div>;
  }

  return (
    <div>
      <h2 style={{ marginBottom: 24 }}>系統總覽</h2>
      
      {/* 基礎數據卡片 */}
      <Row gutter={[16, 16]}>
        <Col span={6} xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="總用戶數"
              value={stats?.totalUsers}
              prefix={<UserOutlined />}
              styles={{ content: {color: '#3f8600'} }}
            />
          </Card>
        </Col>
        <Col span={6} xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="進行中的排程任務"
              value={stats?.activeTasks}
              prefix={<ScheduleOutlined />}
              styles={{ content: {color: '#cf1322'} }}
            />
          </Card>
        </Col>
        <Col span={6} xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="今日訊息數 (Log)"
              value={stats?.todayMessages}
              prefix={<MessageOutlined />}
              suffix="則"
            />
          </Card>
        </Col>
        <Col span={6} xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="系統運行時間"
              value={stats?.systemUptime}
              prefix={<FieldTimeOutlined />}
              styles={{ content: {fontSize: '16px'} }}
            />
          </Card>
        </Col>
      </Row>

      {/* LINE 用量區塊 */}
      {lineUsage && (
        <Card title="LINE 官方帳號本月推播用量" style={{ marginTop: 24 }}>
          <Row gutter={24} align="middle">
            <Col xs={24} md={12} style={{ textAlign: 'center', marginBottom: 16 }}>
              <Progress 
                type="dashboard" 
                percent={lineUsage.totalQuota > 0 ? Math.round((lineUsage.totalUsage / lineUsage.totalQuota) * 100) : 0} 
                status={lineUsage.totalQuota > 0 && (lineUsage.totalUsage / lineUsage.totalQuota) > 0.8 ? 'exception' : 'normal'}
              />
              <div style={{ marginTop: 8, color: '#888' }}>使用率</div>
            </Col>
            <Col xs={24} md={12}>
              <Statistic title="已發送訊息數" value={lineUsage.totalUsage} />
              <Statistic 
                title="本月總額度" 
                value={lineUsage.totalQuota === 0 ? '無限制 (或無法取得)' : lineUsage.totalQuota} 
                style={{ marginTop: 16 }} 
              />
              <div style={{ marginTop: 8 }}>
                方案類型: <span style={{ fontWeight: 'bold' }}>{lineUsage.type}</span>
              </div>
            </Col>
          </Row>
        </Card>
      )}
    </div>
  );
};

export default DashboardHome;
