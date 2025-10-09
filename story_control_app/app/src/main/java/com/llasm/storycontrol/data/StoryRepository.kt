package com.llasm.storycontrol.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 故事数据仓库
 * 包含适合老年人阅读的故事内容
 */
class StoryRepository {
    
    private val stories = mutableMapOf<String, Story>()
    
    init {
        initializeStories()
    }
    
    /**
     * 初始化故事数据
     */
    private fun initializeStories() {
        // 温馨故事
        addStory("2025-01-01", "新年第一缕阳光", """
            新年的第一天，老李早早地起床，走到院子里。
            
            他看见第一缕阳光透过云层洒在院子里，温暖而明亮。
            这让他想起了小时候，母亲总是说："新年的第一缕阳光，会带来一年的好运。"
            
            老李微笑着，心中充满了希望。他知道，无论年龄多大，
            每一天都是新的开始，都值得珍惜和感恩。
            
            他决定今天要去看望老朋友，分享这份新年的喜悦。
        """.trimIndent(), "温馨故事")
        
        addStory("2025-01-02", "邻居的温暖", """
            张奶奶生病了，邻居们都很担心。
            
            王阿姨每天都会熬粥送过去，李叔叔帮忙买菜，
            小孩子们也会在门口轻声问候。
            
            张奶奶感动地说："有你们这样的好邻居，是我这辈子最大的福气。"
            
            邻里之间的关爱，就像冬日里的暖阳，
            温暖着每个人的心。
        """.trimIndent(), "温馨故事")
        
        addStory("2025-01-03", "孝顺的儿子", """
            老陈的儿子在外地工作，每个月都会寄钱回家。
            
            但老陈最开心的不是钱，而是儿子每周的电话。
            电话里，儿子总是关心他的身体，分享工作中的趣事。
            
            老陈说："钱够用就行，最重要的是孩子的心意。"
            
            真正的孝顺，不在于物质的多少，
            而在于那份真诚的关心和陪伴。
        """.trimIndent(), "家庭故事")
        
        addStory("2025-01-04", "老友重逢", """
            在公园里，老刘遇到了多年未见的老同学。
            
            两人坐在长椅上，聊着过去的点点滴滴。
            从学生时代到工作，从家庭到孩子，
            时间仿佛回到了从前。
            
            "时间过得真快啊，"老刘感慨道，
            "但我们的友谊，就像这棵老树一样，越来越深。"
            
            真正的友谊，经得起时间的考验。
        """.trimIndent(), "友谊故事")
        
        addStory("2025-01-05", "学会感恩", """
            老赵每天都会在日记本上写下三件感恩的事。
            
            今天他写的是：
            1. 身体健康，能自己照顾自己
            2. 儿女孝顺，经常打电话关心
            3. 邻居友善，互相帮助
            
            他说："学会感恩，生活就会变得美好。"
            
            感恩的心，是快乐的源泉。
        """.trimIndent(), "智慧故事")
        
        addStory("2025-01-06", "传统的手艺", """
            老木匠李师傅，做了一辈子的木工活。
            
            现在虽然退休了，但他还是喜欢做点小东西。
            邻居家的孩子玩具坏了，他总是免费帮忙修理。
            
            "手艺不能丢，"李师傅说，
            "这是我们的传统，要传承下去。"
            
            传统手艺，承载着文化的记忆。
        """.trimIndent(), "传统故事")
        
        addStory("2025-01-07", "夕阳下的散步", """
            每天傍晚，老夫妻都会手牵手在小区里散步。
            
            他们走得很慢，但很稳。
            路上遇到熟人，总是热情地打招呼。
            
            "一起走了这么多年，"老太太说，
            "最幸福的就是还能一起看夕阳。"
            
            陪伴，是最长情的告白。
        """.trimIndent(), "温馨故事")
        
        addStory("2025-01-08", "社区志愿者", """
            退休后的老张，成为了社区志愿者。
            
            他帮助维护小区环境，组织老人活动，
            还经常看望独居的老人。
            
            "退休不是结束，是新的开始，"老张说，
            "能为社区做点事，我很开心。"
            
            奉献，让生命更有意义。
        """.trimIndent(), "温馨故事")
        
        addStory("2025-01-09", "读书的乐趣", """
            老教师王老师，退休后依然热爱读书。
            
            他的书房里摆满了各种书籍，
            每天都会花时间阅读。
            
            "活到老，学到老，"王老师说，
            "读书让我保持年轻的心态。"
            
            学习，是永不过时的习惯。
        """.trimIndent(), "智慧故事")
        
        addStory("2025-01-10", "花园里的春天", """
            老园丁在自家院子里种了很多花。
            
            春天来了，花儿竞相开放，
            吸引了很多邻居来观赏。
            
            "种花不仅美化了环境，"老园丁说，
            "也让我心情愉悦，身体更健康。"
            
            亲近自然，是最好的养生。
        """.trimIndent(), "温馨故事")
        
        // 添加更多故事...
        addStory("2025-01-11", "孙子的画", """
            小孙子画了一幅画送给爷爷。
            
            画上是一家人围坐在一起吃饭，
            虽然画得不太像，但爷爷看得很开心。
            
            "这是我最珍贵的礼物，"爷爷说，
            "比什么金银珠宝都珍贵。"
            
            孩子的爱，是最纯真的礼物。
        """.trimIndent(), "家庭故事")
        
        addStory("2025-01-12", "老照片的回忆", """
            整理旧物时，老李翻出了很多老照片。
            
            看着照片中年轻的自己和家人，
            他陷入了深深的回忆。
            
            "时间过得真快，"老李感慨道，
            "但美好的回忆永远都在心里。"
            
            回忆，是人生最宝贵的财富。
        """.trimIndent(), "温馨故事")
        
        addStory("2025-01-13", "学会宽容", """
            老邻居之间因为小事发生了争执。
            
            老李主动去道歉，化解了矛盾。
            "邻里之间，要互相理解，"老李说，
            "宽容一点，大家都开心。"
            
            宽容，是人际关系的润滑剂。
        """.trimIndent(), "智慧故事")
        
        addStory("2025-01-14", "传统节日", """
            春节到了，老王家准备了很多传统食物。
            
            全家人围坐在一起包饺子，
            孩子们学着包，虽然包得不好看，但很开心。
            
            "传统节日，就是要一家人在一起，"老王说，
            "这样才有年味。"
            
            传统节日，承载着家的温暖。
        """.trimIndent(), "传统故事")
        
        addStory("2025-01-15", "健康的生活", """
            老医生退休后，依然坚持健康的生活方式。
            
            每天早起锻炼，饮食清淡，
            还经常给邻居们分享健康知识。
            
            "健康是最大的财富，"老医生说，
            "要好好珍惜自己的身体。"
            
            健康，是幸福生活的基础。
        """.trimIndent(), "智慧故事")
        
        // 继续添加更多故事，确保有足够的内容
        for (i in 16..31) {
            val dateStr = "2025-01-${i.toString().padStart(2, '0')}"
            addStory(dateStr, "每日故事 $i", """
                这是第 $i 天的故事内容。
                
                每天都有新的故事，新的感悟。
                生活就像一本书，每一页都值得细细品味。
                
                愿每一天都充满阳光和希望。
            """.trimIndent(), "温馨故事")
        }
    }
    
    /**
     * 添加故事
     */
    private fun addStory(dateStr: String, title: String, content: String, category: String) {
        val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        val story = Story(
            id = dateStr,
            title = title,
            content = content,
            date = date,
            category = category
        )
        stories[dateStr] = story
    }
    
    /**
     * 根据日期获取故事
     */
    fun getStoryByDate(date: LocalDate): Story? {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return stories[dateStr]
    }
    
    /**
     * 获取今天的故事
     */
    fun getTodayStory(): Story? {
        return getStoryByDate(LocalDate.now())
    }
    
    /**
     * 获取所有故事
     */
    fun getAllStories(): List<Story> {
        return stories.values.sortedBy { it.date }
    }
    
    /**
     * 根据类别获取故事
     */
    fun getStoriesByCategory(category: String): List<Story> {
        return stories.values.filter { it.category == category }
            .sortedBy { it.date }
    }
}
