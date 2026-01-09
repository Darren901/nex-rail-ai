import React, { useEffect, useState } from 'react';
import { Card, Button, Row, Col, Statistic, message, Popconfirm } from 'antd';
import { ReloadOutlined, DeleteOutlined, DatabaseOutlined } from '@ant-design/icons';
import { getCacheStats, clearCache, type CacheStats } from '../services/cache';

const CacheManager: React.FC = () => {
  const [stats, setStats] = useState<CacheStats | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchStats = async () => {
    setLoading(true);
    try {
      const res = await getCacheStats();
      setStats(res);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStats();
  }, []);

  const handleClear = async (type: string) => {
    try {
      const res = await clearCache(type);
      message.success(res.message);
      fetchStats();
    } catch (error) {
      console.error(error);
    }
  };

  const CacheCard = ({ title, value, type, danger = false }: { title: string, value: number, type: string, danger?: boolean }) => (
    <Col span={8} xs={24} sm={12} md={8}>
      <Card hoverable style={{ height: '100%' }}>
        <Statistic
          title={title}
          value={value}
          prefix={<DatabaseOutlined />}
        />
        <div style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}>
          <Popconfirm
            title={`確定要清除所有${title}嗎？`}
            description="清除後系統將需要重新從來源獲取資料，可能會暫時影響效能。"
            onConfirm={() => handleClear(type)}
            okText="是"
            cancelText="否"
          >
            <Button danger={danger} icon={<DeleteOutlined />}>清除快取</Button>
          </Popconfirm>
        </div>
      </Card>
    </Col>
  );

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2>系統快取管理</h2>
        <Button icon={<ReloadOutlined />} onClick={fetchStats} loading={loading}>重新整理</Button>
      </div>

      {stats && (
        <Card style={{ marginBottom: 24, background: '#fafafa' }}>
          <Row gutter={16}>
            <Col span={6}>
              <Statistic title="快取 Key 總數" value={stats.totalKeys} styles={{ content: { color: '#1890ff' } }}  />
            </Col>
            <Col span={18}>
              <div style={{ display: 'flex', alignItems: 'center', height: '100%', color: '#888' }}>
                <DatabaseOutlined style={{ marginRight: 8 }} />
                目前系統正在運作中，各類別快取數據如下方詳細列表所示。
              </div>
            </Col>
          </Row>
        </Card>
      )}

      <Row gutter={[16, 16]}>
        {stats && (
          <>
            <CacheCard title="TDX Token" value={stats.tokenKeys} type="TOKEN" />
            <CacheCard title="時刻表資料" value={stats.timetableKeys} type="TIMETABLE" />
            <CacheCard title="票價資料" value={stats.fareKeys} type="FARES" />
            <CacheCard title="每日 AI 對話額度" value={stats.dailyQuotaKeys} type="DAILY_QUOTA" danger />
            <CacheCard title="每月推播額度" value={stats.monthlyNotifyKeys} type="MONTHLY_NOTIFY" danger />
          </>
        )}
      </Row>
    </div>
  );
};

export default CacheManager;
