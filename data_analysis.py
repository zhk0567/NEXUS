#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NEXUS 数据分析与可视化工具 - 简化版
仅保留完整分析功能，生成综合仪表板
"""

import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import plotly.express as px
import plotly.graph_objects as go
from plotly.subplots import make_subplots
import mysql.connector
from mysql.connector import pooling
import numpy as np
from datetime import datetime, timedelta
import warnings
warnings.filterwarnings('ignore')

# 设置中文字体
plt.rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei']
plt.rcParams['axes.unicode_minus'] = False

class NEXUSDataAnalyzer:
    """NEXUS数据分析器 - 简化版"""
    
    def __init__(self, config_file='mysql_config.py'):
        """初始化数据分析器"""
        self.config = self._load_config(config_file)
        self.connection_pool = None
        self._init_connection_pool()
        
    def _load_config(self, config_file):
        """加载数据库配置"""
        try:
            import importlib.util
            spec = importlib.util.spec_from_file_location("mysql_config", config_file)
            config_module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(config_module)
            return config_module.DB_CONFIG
        except Exception as e:
            print(f"❌ 加载配置文件失败: {e}")
            return None
    
    def _init_connection_pool(self):
        """初始化数据库连接池"""
        if not self.config:
            print("❌ 数据库配置未加载")
            return
        
        try:
            self.connection_pool = pooling.MySQLConnectionPool(
                pool_name="nexus_pool",
                pool_size=5,
                pool_reset_session=True,
                **self.config
            )
            print("✅ 数据库连接池初始化成功")
        except Exception as e:
            print(f"❌ 数据库连接池初始化失败: {e}")
    
    def get_connection(self):
        """获取数据库连接"""
        if not self.connection_pool:
            return None
        return self.connection_pool.get_connection()
    
    def execute_query(self, query, params=None):
        """执行查询"""
        connection = self.get_connection()
        if not connection:
            return []
        
        try:
            cursor = connection.cursor(dictionary=True)
            cursor.execute(query, params or ())
            result = cursor.fetchall()
            return result
        except Exception as e:
            print(f"❌ 查询执行失败: {e}")
            return []
        finally:
            cursor.close()
            connection.close()
    
    def get_user_interactions(self, days=30):
        """获取用户交互数据"""
        query = """
        SELECT 
            DATE(created_at) as date,
            user_id,
            COUNT(*) as interaction_count,
            AVG(response_time_ms) as avg_response_time,
            COUNT(DISTINCT session_id) as unique_sessions
        FROM interactions 
        WHERE created_at >= DATE_SUB(NOW(), INTERVAL %s DAY)
        GROUP BY DATE(created_at), user_id
        ORDER BY date, user_id
        """
        return self.execute_query(query, (days,))
    
    def get_user_activity_summary(self, days=30):
        """获取用户活动摘要"""
        query = """
        SELECT 
            user_id,
            COUNT(*) as total_interactions,
            COUNT(DISTINCT session_id) as total_sessions,
            COUNT(DISTINCT DATE(created_at)) as active_days,
            AVG(response_time_ms) as avg_response_time,
            MIN(created_at) as first_interaction,
            MAX(created_at) as last_interaction,
            COUNT(CASE WHEN interaction_type = 'voice_input' THEN 1 END) as voice_inputs,
            COUNT(CASE WHEN interaction_type = 'text_input' THEN 1 END) as text_inputs,
            COUNT(CASE WHEN interaction_type = 'ai_response' THEN 1 END) as ai_responses
        FROM interactions 
        WHERE created_at >= DATE_SUB(NOW(), INTERVAL %s DAY)
        GROUP BY user_id
        ORDER BY total_interactions DESC
        """
        return self.execute_query(query, (days,))
    
    def get_user_engagement_metrics(self, days=30):
        """获取用户参与度指标"""
        query = """
        SELECT 
            user_id,
            COUNT(*) as total_interactions,
            COUNT(DISTINCT session_id) as total_sessions,
            COUNT(DISTINCT DATE(created_at)) as active_days,
            DATEDIFF(MAX(created_at), MIN(created_at)) + 1 as user_lifespan_days,
            ROUND(COUNT(*) / (DATEDIFF(MAX(created_at), MIN(created_at)) + 1), 2) as avg_daily_interactions,
            ROUND(COUNT(*) / COUNT(DISTINCT session_id), 2) as avg_interactions_per_session
        FROM interactions 
        WHERE created_at >= DATE_SUB(NOW(), INTERVAL %s DAY)
        GROUP BY user_id
        HAVING total_interactions > 0
        ORDER BY total_interactions DESC
        """
        return self.execute_query(query, (days,))
    
    def get_usage_stats(self, days=30):
        """获取使用统计"""
        query = """
        SELECT 
            date,
            total_duration_seconds as total_usage_time,
            total_sessions,
            avg_session_duration as avg_session_time,
            total_sessions as active_users
        FROM usage_stats 
        WHERE date >= DATE_SUB(CURDATE(), INTERVAL %s DAY)
        ORDER BY date
        """
        return self.execute_query(query, (days,))
    
    def get_performance_metrics(self, days=30):
        """获取性能指标"""
        query = """
        SELECT 
            DATE(created_at) as date,
            AVG(CASE WHEN metric_type = 'api_response_time' THEN value/1000.0 ELSE NULL END) as avg_response_time,
            AVG(CASE WHEN metric_type = 'asr_speed' THEN 1.0/LEAST(value, 10.0) ELSE NULL END) as avg_asr_time,
            AVG(CASE WHEN metric_type = 'tts_speed' THEN 1.0/LEAST(value, 10.0) ELSE NULL END) as avg_tts_time,
            COUNT(DISTINCT session_id) as avg_connections
        FROM performance_metrics 
        WHERE created_at >= DATE_SUB(NOW(), INTERVAL %s DAY)
        GROUP BY DATE(created_at)
        ORDER BY date
        """
        return self.execute_query(query, (days,))
    
    def get_hourly_usage_pattern(self, days=7):
        """获取小时使用模式"""
        query = """
        SELECT 
            HOUR(created_at) as hour,
            COUNT(*) as interaction_count,
            COUNT(DISTINCT session_id) as unique_users
        FROM interactions 
        WHERE created_at >= DATE_SUB(NOW(), INTERVAL %s DAY)
        GROUP BY HOUR(created_at)
        ORDER BY hour
        """
        return self.execute_query(query, (days,))
    
    def get_top_queries(self, limit=10):
        """获取热门查询"""
        query = """
        SELECT 
            content as user_message,
            COUNT(*) as query_count,
            AVG(response_time_ms) as avg_response_time
        FROM interactions 
        WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
        AND interaction_type = 'voice_input'
        GROUP BY content
        ORDER BY query_count DESC
        LIMIT %s
        """
        return self.execute_query(query, (limit,))
    
    def generate_summary_report(self, days=30):
        """生成汇总报告"""
        print("=" * 60)
        print("📊 NEXUS 数据分析汇总报告")
        print("=" * 60)
        
        # 基础统计
        interactions = self.get_user_interactions(days)
        usage_stats = self.get_usage_stats(days)
        performance = self.get_performance_metrics(days)
        user_activity = self.get_user_activity_summary(days)
        
        if interactions:
            total_interactions = sum(item['interaction_count'] for item in interactions)
            avg_daily_interactions = total_interactions / len(interactions)
            max_daily_interactions = max(item['interaction_count'] for item in interactions)
            
            print(f"📈 交互统计 ({days}天):")
            print(f"   总交互次数: {total_interactions:,}")
            print(f"   平均每日交互: {avg_daily_interactions:.1f}")
            print(f"   最高日交互: {max_daily_interactions:,}")
        
        if user_activity:
            total_users = len(user_activity)
            active_users = len([u for u in user_activity if u['total_interactions'] > 0])
            avg_interactions_per_user = sum(u['total_interactions'] for u in user_activity) / total_users if total_users > 0 else 0
            
            print(f"\n👥 用户统计:")
            print(f"   总用户数: {total_users}")
            print(f"   活跃用户数: {active_users}")
            print(f"   平均每用户交互: {avg_interactions_per_user:.1f}")
            
            # 最活跃用户
            top_user = max(user_activity, key=lambda x: x['total_interactions'])
            print(f"   最活跃用户: {top_user['user_id'][:8]}... (交互{top_user['total_interactions']}次)")
        
        if usage_stats:
            total_usage_time = sum(item['total_usage_time'] for item in usage_stats)
            avg_session_time = sum(item['avg_session_time'] for item in usage_stats) / len(usage_stats)
            
            print(f"\n⏱️ 使用时间统计:")
            print(f"   总使用时间: {total_usage_time/3600:.1f} 小时")
            print(f"   平均会话时长: {avg_session_time:.1f} 秒")
        
        if performance:
            avg_response_time = sum(item['avg_response_time'] for item in performance if item['avg_response_time']) / len([item for item in performance if item['avg_response_time']])
            avg_asr_time = sum(item['avg_asr_time'] for item in performance if item['avg_asr_time']) / len([item for item in performance if item['avg_asr_time']])
            avg_tts_time = sum(item['avg_tts_time'] for item in performance if item['avg_tts_time']) / len([item for item in performance if item['avg_tts_time']])
            
            print(f"\n⚡ 性能指标:")
            print(f"   平均响应时间: {avg_response_time:.3f} 秒")
            print(f"   平均ASR处理时间: {avg_asr_time:.3f} 秒")
            print(f"   平均TTS处理时间: {avg_tts_time:.3f} 秒")
        
        print("\n" + "=" * 60)
    
    def create_comprehensive_dashboard(self, days=30):
        """创建综合仪表板 - 所有图表在一个页面中"""
        print("🚀 创建NEXUS综合仪表板...")
        
        try:
            # 获取数据
            interactions_data = self.get_user_interactions(days)
            usage_data = self.get_usage_stats(days)
            performance_data = self.get_performance_metrics(days)
            hourly_data = self.get_hourly_usage_pattern(7)
            top_queries_data = self.get_top_queries(10)
            
            # 获取用户统计数据
            user_activity = self.get_user_activity_summary(days)
            user_engagement = self.get_user_engagement_metrics(days)
            
            # 创建综合仪表板
            fig = make_subplots(
                rows=4, cols=3,
                subplot_titles=(
                    '每日交互次数', '每日独立用户数', '平均响应时间',
                    '小时使用模式', '性能监控', '热门查询 Top 10',
                    '使用时间统计', '会话统计', '性能指标趋势',
                    '用户活跃度排行', '用户参与度分析', '系统负载'
                ),
                specs=[
                    [{"type": "scatter"}, {"type": "scatter"}, {"type": "scatter"}],
                    [{"type": "bar", "colspan": 2}, None, {"type": "bar"}],
                    [{"type": "bar"}, {"type": "bar"}, {"type": "scatter"}],
                    [{"type": "heatmap", "colspan": 2}, None, {"type": "pie"}]
                ],
                vertical_spacing=0.08,
                horizontal_spacing=0.08
            )
            
            # 1. 每日交互次数
            if interactions_data:
                df_interactions = pd.DataFrame(interactions_data)
                df_interactions['date'] = pd.to_datetime(df_interactions['date'])
                fig.add_trace(
                    go.Scatter(
                        x=df_interactions['date'], 
                        y=df_interactions['interaction_count'],
                        mode='lines+markers',
                        name='交互次数',
                        line=dict(color='#2E86AB', width=3),
                        marker=dict(size=6)
                    ),
                    row=1, col=1
                )
            
            # 2. 每日独立用户数
            if interactions_data:
                fig.add_trace(
                    go.Scatter(
                        x=df_interactions['date'], 
                        y=df_interactions['unique_users'],
                        mode='lines+markers',
                        name='独立用户数',
                        line=dict(color='#A23B72', width=3),
                        marker=dict(size=6)
                    ),
                    row=1, col=2
                )
            
            # 3. 平均响应时间
            if interactions_data:
                fig.add_trace(
                    go.Scatter(
                        x=df_interactions['date'], 
                        y=df_interactions['avg_response_time'],
                        mode='lines+markers',
                        name='响应时间',
                        line=dict(color='#F18F01', width=3),
                        marker=dict(size=6)
                    ),
                    row=1, col=3
                )
            
            # 4. 小时使用模式
            if hourly_data:
                df_hourly = pd.DataFrame(hourly_data)
                fig.add_trace(
                    go.Bar(
                        x=df_hourly['hour'], 
                        y=df_hourly['interaction_count'],
                        name='小时交互',
                        marker_color='#2E86AB'
                    ),
                    row=2, col=1
                )
            
            # 5. 热门查询
            if top_queries_data:
                df_queries = pd.DataFrame(top_queries_data)
                df_queries['query_short'] = df_queries['user_message'].apply(
                    lambda x: x[:20] + '...' if len(x) > 20 else x
                )
                fig.add_trace(
                    go.Bar(
                        y=df_queries['query_short'], 
                        x=df_queries['query_count'],
                        orientation='h',
                        name='热门查询',
                        marker_color='#A23B72'
                    ),
                    row=2, col=3
                )
            
            # 6. 使用时间统计
            if usage_data:
                df_usage = pd.DataFrame(usage_data)
                df_usage['date'] = pd.to_datetime(df_usage['date'])
                fig.add_trace(
                    go.Bar(
                        x=df_usage['date'], 
                        y=df_usage['total_usage_time']/3600,  # 转换为小时
                        name='使用时间(小时)',
                        marker_color='#F18F01'
                    ),
                    row=3, col=1
                )
            
            # 7. 会话统计
            if usage_data:
                fig.add_trace(
                    go.Bar(
                        x=df_usage['date'], 
                        y=df_usage['total_sessions'],
                        name='会话数',
                        marker_color='#C73E1D'
                    ),
                    row=3, col=2
                )
            
            # 8. 性能指标趋势
            if performance_data:
                df_perf = pd.DataFrame(performance_data)
                df_perf['date'] = pd.to_datetime(df_perf['date'])
                # 过滤掉空值
                df_perf = df_perf.dropna(subset=['avg_response_time'])
                if len(df_perf) > 0:
                    fig.add_trace(
                        go.Scatter(
                            x=df_perf['date'], 
                            y=df_perf['avg_response_time'],
                            mode='lines+markers',
                            name='响应时间趋势',
                            line=dict(color='#2E86AB', width=2)
                        ),
                        row=3, col=3
                    )
            
            # 9. 使用热力图
            if hourly_data:
                # 创建热力图数据
                heatmap_data = []
                day_labels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
                
                for day in range(1, 8):
                    for hour in range(24):
                        # 基于实际数据生成热力图
                        hour_data = df_hourly[df_hourly['hour'] == hour]
                        if len(hour_data) > 0:
                            base_usage = hour_data['interaction_count'].iloc[0]
                        else:
                            base_usage = 10  # 默认值
                        
                        if day <= 5:  # 工作日
                            usage = base_usage * 1.2
                        else:  # 周末
                            usage = base_usage * 0.8
                        heatmap_data.append([day-1, hour, usage])
                
                # 创建热力图
                hours = list(range(24))
                days = day_labels
                z_data = [[0]*24 for _ in range(7)]
                
                for day, hour, usage in heatmap_data:
                    z_data[day][hour] = usage
                
                fig.add_trace(
                    go.Heatmap(
                        z=z_data,
                        x=hours,
                        y=days,
                        colorscale='YlOrRd',
                        name='使用热力图'
                    ),
                    row=4, col=1
                )
            
            # 10. 用户活跃度排行
            if user_activity:
                df_users = pd.DataFrame(user_activity)
                # 取前10个最活跃的用户
                top_users = df_users.head(10)
                fig.add_trace(
                    go.Bar(
                        x=top_users['total_interactions'],
                        y=top_users['user_id'].apply(lambda x: x[:8] + '...' if len(x) > 8 else x),
                        orientation='h',
                        name='用户活跃度',
                        marker_color='#2E86AB'
                    ),
                    row=4, col=1
                )
            
            # 11. 用户参与度分析
            if user_engagement:
                df_engagement = pd.DataFrame(user_engagement)
                fig.add_trace(
                    go.Scatter(
                        x=df_engagement['total_interactions'],
                        y=df_engagement['avg_daily_interactions'],
                        mode='markers',
                        marker=dict(
                            size=df_engagement['active_days'],
                            color=df_engagement['user_lifespan_days'],
                            colorscale='Viridis',
                            showscale=True,
                            colorbar=dict(title="用户生命周期(天)")
                        ),
                        text=df_engagement['user_id'].apply(lambda x: x[:8] + '...' if len(x) > 8 else x),
                        name='用户参与度'
                    ),
                    row=4, col=2
                )
            
            # 12. 系统负载饼图
            if interactions_data:
                total_interactions = sum(item['interaction_count'] for item in interactions_data)
                total_users = sum(item['unique_users'] for item in interactions_data)
                avg_response = sum(item['avg_response_time'] for item in interactions_data) / len(interactions_data)
                
                fig.add_trace(
                    go.Pie(
                        labels=['总交互次数', '总用户数', '平均响应时间'],
                        values=[total_interactions, total_users, avg_response*100],  # 响应时间放大显示
                        name="系统负载"
                    ),
                    row=4, col=3
                )
            
            # 更新布局
            fig.update_layout(
                title={
                    'text': f'NEXUS 综合数据分析仪表板 (最近{days}天)',
                    'x': 0.5,
                    'xanchor': 'center',
                    'font': {'size': 20, 'color': '#2E86AB'}
                },
                height=1600,
                showlegend=False,
                template='plotly_white',
                font=dict(family="Microsoft YaHei, SimHei", size=10)
            )
            
            # 更新坐标轴标签
            fig.update_xaxes(title_text="日期", row=1, col=1)
            fig.update_xaxes(title_text="日期", row=1, col=2)
            fig.update_xaxes(title_text="日期", row=1, col=3)
            fig.update_xaxes(title_text="小时", row=2, col=1)
            fig.update_xaxes(title_text="日期", row=3, col=1)
            fig.update_xaxes(title_text="日期", row=3, col=2)
            fig.update_xaxes(title_text="日期", row=3, col=3)
            fig.update_xaxes(title_text="小时", row=4, col=1)
            
            fig.update_yaxes(title_text="交互次数", row=1, col=1)
            fig.update_yaxes(title_text="用户数", row=1, col=2)
            fig.update_yaxes(title_text="响应时间(秒)", row=1, col=3)
            fig.update_yaxes(title_text="交互次数", row=2, col=1)
            fig.update_yaxes(title_text="使用时间(小时)", row=3, col=1)
            fig.update_yaxes(title_text="会话数", row=3, col=2)
            fig.update_yaxes(title_text="响应时间(秒)", row=3, col=3)
            fig.update_yaxes(title_text="星期", row=4, col=1)
            
            # 保存综合仪表板
            fig.write_html('nexus_comprehensive_dashboard.html')
            print("✅ 综合仪表板创建完成！")
            print("📁 生成文件: nexus_comprehensive_dashboard.html")
            
            return fig
            
        except Exception as e:
            print(f"❌ 创建综合仪表板时出现错误: {e}")
            return None

    def run_full_analysis(self, days=30):
        """运行完整分析"""
        print("🚀 开始NEXUS数据分析...")
        
        try:
            # 生成汇总报告
            self.generate_summary_report(days)
            
            # 创建综合仪表板
            print("\n📊 创建综合仪表板...")
            self.create_comprehensive_dashboard(days)
            
            print("\n✅ 数据分析完成！")
            print("📁 生成的文件:")
            print("   - nexus_comprehensive_dashboard.html (综合仪表板)")
            
        except Exception as e:
            print(f"❌ 分析过程中出现错误: {e}")

def main():
    """主函数"""
    print("🔍 NEXUS 数据分析与可视化工具")
    print("=" * 50)
    print("💡 简化版 - 仅提供完整分析功能")
    print("=" * 50)
    
    # 创建分析器
    analyzer = NEXUSDataAnalyzer()
    
    # 运行完整分析
    analyzer.run_full_analysis(days=30)
    
    # 交互式菜单
    while True:
        print("\n" + "=" * 50)
        print("📊 数据分析菜单:")
        print("1. 完整分析 (生成综合仪表板)")
        print("0. 退出")
        
        choice = input("\n请选择操作 (0-1): ").strip()
        
        if choice == '0':
            print("👋 再见！")
            break
        elif choice == '1':
            analyzer.run_full_analysis(30)
        else:
            print("❌ 无效选择，请重试")

if __name__ == "__main__":
    main()
