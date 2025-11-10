#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Initialize story data to database
Extract story data from Android client's StoryRepository.kt and sync to database
"""
import sys
import os
# Set UTF-8 encoding for Windows console
if sys.platform == 'win32':
    os.system('chcp 65001 >nul')
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')

from database_manager import db_manager

# 故事数据（从StoryRepository.kt提取）
STORIES = [
    {
        "story_id": "2025-01-01",
        "title": "新年第一缕阳光",
        "content": """新年的第一天，老李早早地起床，走到院子里。

他看见第一缕阳光透过云层洒在院子里，温暖而明亮。
这让他想起了小时候，母亲总是说："新年的第一缕阳光，会带来一年的好运。"

老李微笑着，心中充满了希望。他知道，无论年龄多大，
每一天都是新的开始，都值得珍惜和感恩。

他决定今天要去看望老朋友，分享这份新年的喜悦。""",
        "audio_file_path": "story_audio/2024-01-01.mp3",  # 音频文件是2024格式
        "audio_duration_seconds": None
    },
    {
        "story_id": "2025-01-02",
        "title": "邻居的温暖",
        "content": """张奶奶生病了，邻居们都很担心。

王阿姨每天都会熬粥送过去，李叔叔帮忙买菜，
小孩子们也会在门口轻声问候。

张奶奶感动地说："有你们这样的好邻居，是我这辈子最大的福气。"

邻里之间的关爱，就像冬日里的暖阳，
温暖着每个人的心。""",
        "audio_file_path": "story_audio/2024-01-02.mp3",
        "audio_duration_seconds": None
    },
    {
        "story_id": "2025-01-03",
        "title": "孝顺的儿子",
        "content": """老陈的儿子在外地工作，每个月都会寄钱回家。

但老陈最开心的不是钱，而是儿子每周的电话。
电话里，儿子总是关心他的身体，分享工作中的趣事。

老陈说："钱够用就行，最重要的是孩子的心意。"

真正的孝顺，不在于物质的多少，
而在于那份真诚的关心和陪伴。""",
        "audio_file_path": "story_audio/2024-01-03.mp3",
        "audio_duration_seconds": None
    },
    {
        "story_id": "2025-01-04",
        "title": "老友重逢",
        "content": """在公园里，老刘遇到了多年未见的老同学。

两人坐在长椅上，聊着过去的点点滴滴。
从学生时代到工作，从家庭到孩子，
时间仿佛回到了从前。

"时间过得真快啊，"老刘感慨道，
"但我们的友谊，就像这棵老树一样，越来越深。"

真正的友谊，经得起时间的考验。""",
        "audio_file_path": "story_audio/2024-01-04.mp3",
        "audio_duration_seconds": None
    },
    {
        "story_id": "2025-01-05",
        "title": "学会感恩",
        "content": """老赵每天都会在日记本上写下三件感恩的事。

今天他写的是：
1. 身体健康，能自己照顾自己
2. 儿女孝顺，经常打电话关心
3. 邻居友善，互相帮助

他说："学会感恩，生活就会变得美好。"

感恩的心，是快乐的源泉。""",
        "audio_file_path": "story_audio/2024-01-05.mp3",
        "audio_duration_seconds": None
    },
    {
        "story_id": "2025-01-06",
        "title": "传统的手艺",
        "content": """老木匠李师傅，做了一辈子的木工活。

现在虽然退休了，但他还是喜欢做点小东西。
邻居家的孩子玩具坏了，他总是免费帮忙修理。

"手艺不能丢，"李师傅说，
"这是我们的传统，要传承下去。"

传统手艺，承载着文化的记忆。""",
        "audio_file_path": "story_audio/2024-01-06.mp3",
        "audio_duration_seconds": None
    },
    {
        "story_id": "2025-01-07",
        "title": "夕阳下的散步",
        "content": """每天傍晚，老夫妻都会手牵手在小区里散步。

他们走得很慢，但很稳。
路上遇到熟人，总是热情地打招呼。

"一起走了这么多年，"老太太说，
"最幸福的就是还能一起看夕阳。"

陪伴，是最长情的告白。""",
        "audio_file_path": "story_audio/2024-01-07.mp3",
        "audio_duration_seconds": None
    },
    {
        "story_id": "2025-01-08",
        "title": "社区志愿者",
        "content": """退休后的老张，成为了社区志愿者。

他帮助维护小区环境，组织老人活动，
还经常看望独居的老人。

"退休不是结束，是新的开始，"老张说，
"能为社区做点事，我很开心。"

奉献，让生命更有意义。""",
        "audio_file_path": "story_audio/2024-01-08.mp3",
        "audio_duration_seconds": None
    },
    {
        "story_id": "2025-01-09",
        "title": "读书的乐趣",
        "content": """老教师王老师，退休后依然热爱读书。

他的书房里摆满了各种书籍，
每天都会花时间阅读。

"活到老，学到老，"王老师说，
"读书让我保持年轻的心态。"

学习，是永不过时的习惯。""",
        "audio_file_path": "story_audio/2024-01-09.mp3",
        "audio_duration_seconds": None
    },
    {
        "story_id": "2025-01-10",
        "title": "花园里的春天",
        "content": """老园丁在自家院子里种了很多花。

春天来了，花儿竞相开放，
吸引了很多邻居来观赏。

"种花不仅美化了环境，"老园丁说，
"也让我心情愉悦，身体更健康。"

亲近自然，是最好的养生。""",
        "audio_file_path": "story_audio/2024-01-10.mp3",
        "audio_duration_seconds": None
    },
    {
        "story_id": "2025-01-11",
        "title": "孙子的画",
        "content": """小孙子画了一幅画送给爷爷。

画上是一家人围坐在一起吃饭，
虽然画得不太像，但爷爷看得很开心。

"这是我最珍贵的礼物，"爷爷说，
"比什么金银珠宝都珍贵。"

孩子的爱，是最纯真的礼物。""",
        "audio_file_path": "story_audio/2024-01-11.mp3",
        "audio_duration_seconds": None
    },
    {
        "story_id": "2025-01-12",
        "title": "老照片的回忆",
        "content": """整理旧物时，老李翻出了很多老照片。

看着照片中年轻的自己和家人，
他陷入了深深的回忆。

"时间过得真快，"老李感慨道，
"但美好的回忆永远都在心里。"

回忆，是人生最宝贵的财富。""",
        "audio_file_path": "story_audio/2024-01-12.mp3",
        "audio_duration_seconds": None
    },
    {
        "story_id": "2025-01-13",
        "title": "学会宽容",
        "content": """老邻居之间因为小事发生了争执。

老李主动去道歉，化解了矛盾。
"邻里之间，要互相理解，"老李说，
"宽容一点，大家都开心。"

宽容，是人际关系的润滑剂。""",
        "audio_file_path": "story_audio/2024-01-13.mp3",
        "audio_duration_seconds": None
    },
    {
        "story_id": "2025-01-14",
        "title": "传统节日",
        "content": """春节到了，老王家准备了很多传统食物。

全家人围坐在一起包饺子，
孩子们学着包，虽然包得不好看，但很开心。

"传统节日，就是要一家人在一起，"老王说，
"这样才有年味。"

传统节日，承载着家的温暖。""",
        "audio_file_path": "story_audio/2024-01-14.mp3",
        "audio_duration_seconds": None
    },
    {
        "story_id": "2025-01-15",
        "title": "健康的生活",
        "content": """老医生退休后，依然坚持健康的生活方式。

每天早起锻炼，饮食清淡，
还经常给邻居们分享健康知识。

"健康是最大的财富，"老医生说，
"要好好珍惜自己的身体。"

健康，是幸福生活的基础。""",
        "audio_file_path": "story_audio/2024-01-15.mp3",
        "audio_duration_seconds": None
    }
]

# 添加16-31日的故事
for i in range(16, 32):
    date_str = f"2025-01-{i:02d}"
    STORIES.append({
        "story_id": date_str,
        "title": f"每日故事 {i}",
        "content": f"""这是第 {i} 天的故事内容。

每天都有新的故事，新的感悟。
生活就像一本书，每一页都值得细细品味。

愿每一天都充满阳光和希望。""",
        "audio_file_path": f"story_audio/2024-01-{i:02d}.mp3",
        "audio_duration_seconds": None
    })


def init_stories():
    """Initialize story data to database"""
    print("[INFO] Starting story data initialization...")
    
    success_count = 0
    skip_count = 0
    error_count = 0
    
    for story in STORIES:
        story_id = story["story_id"]
        
        # Check if story already exists
        existing_story = db_manager.get_story(story_id)
        if existing_story:
            print(f"[SKIP] Story already exists, skipping: {story_id} - {story['title']}")
            skip_count += 1
            continue
        
        # Create story
        success = db_manager.create_story(
            story_id=story_id,
            title=story["title"],
            content=story["content"],
            audio_file_path=story["audio_file_path"],
            audio_duration_seconds=story["audio_duration_seconds"],
            created_by=None  # 设置为None，因为外键允许NULL
        )
        
        if success:
            print(f"[OK] Story created successfully: {story_id} - {story['title']}")
            success_count += 1
        else:
            print(f"[ERROR] Failed to create story: {story_id} - {story['title']}")
            error_count += 1
    
    print("\n" + "="*50)
    print(f"[SUMMARY] Initialization completed:")
    print(f"   Success: {success_count}")
    print(f"   Skipped: {skip_count}")
    print(f"   Failed: {error_count}")
    print("="*50)


if __name__ == "__main__":
    try:
        init_stories()
    except Exception as e:
        print(f"[ERROR] Initialization failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

