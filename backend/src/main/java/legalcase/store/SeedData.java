package legalcase.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 首次启动时写入 data/cases.json 的示例数据。 */
final class SeedData {

    private SeedData() {}

    static List<Map<String, Object>> defaultCases() {
        List<Map<String, Object>> list = new ArrayList<>();

        // ---------- 案例 1：已发布，含完整章节与备注 ----------
        Map<String, Object> c1 = new LinkedHashMap<>();
        c1.put("id", "c001");
        c1.put("title", "张某诉某科技公司劳动争议案");
        c1.put("caseNumber", "（2024）京0105民初12345号");
        c1.put("court", "北京市朝阳区人民法院");
        c1.put("status", "published");
        c1.put("summary", "劳动者主张用人单位违法解除劳动合同，要求继续履行合同并支付违法解除期间工资；用人单位主张系试用期不符合录用条件的合法解除。");
        c1.put("facts", "张某于2023年3月1日入职某科技公司，担任后端开发工程师，劳动合同期限三年，约定试用期六个月。2023年7月20日，公司以“试用期内被证明不符合录用条件”为由通知解除劳动合同。张某认为公司未事先明确录用条件，解除行为违法，遂提起劳动仲裁后诉至法院。");
        List<Map<String, Object>> c1chs = new ArrayList<>();
        c1chs.add(chapter("ch101", "争议焦点梳理",
                "1. 公司是否已向劳动者明示具体录用条件；\n2. “不符合录用条件”是否有客观考核依据；\n3. 解除程序是否符合法律规定（说明理由、通知工会）。", 1));
        c1chs.add(chapter("ch102", "法律依据适用",
                "《劳动合同法》第三十九条第一项：劳动者在试用期间被证明不符合录用条件的，用人单位可以解除劳动合同。\n适用要点：举证责任在用人单位，须同时证明“录用条件已明示”与“不符合条件有事实依据”。", 2));
        c1chs.add(chapter("ch103", "裁判要旨与课堂讨论",
                "法院认为公司仅在员工手册中作概括性描述，未针对张某岗位明示可量化的录用条件，考核结果亦缺乏过程性记录，解除依据不足，构成违法解除。\n讨论：用人单位如何在入职环节设计合规的“录用条件确认书”？", 3));
        c1.put("chapters", c1chs);
        List<Map<String, Object>> c1notes = new ArrayList<>();
        c1notes.add(note("n201", "课堂前10分钟先让学生分组站在劳动者/用人单位两边列证据清单，再进入焦点讲解。", "2026-09-10 09:20:00"));
        c1notes.add(note("n202", "可延伸对比《劳动合同法》第四十条（不能胜任工作）与第三十九条的举证差异，提醒不要混淆。", "2026-09-12 14:05:00"));
        c1.put("notes", c1notes);
        c1.put("createdAt", "2026-09-08 10:00:00");
        c1.put("updatedAt", "2026-09-12 14:05:00");
        list.add(c1);

        // ---------- 案例 2：草稿（学生端不可见） ----------
        Map<String, Object> c2 = new LinkedHashMap<>();
        c2.put("id", "c002");
        c2.put("title", "李某与王某房屋买卖合同纠纷案");
        c2.put("caseNumber", "（2025）沪0115民初6789号");
        c2.put("court", "上海市浦东新区人民法院");
        c2.put("status", "draft");
        c2.put("summary", "房屋买卖合同签订后房价上涨，出卖人拒绝履行，买受人主张继续履行并要求违约金。（草稿，章节待补充）");
        c2.put("facts", "2024年11月，李某（买受人）与王某（出卖人）签订二手房买卖合同，约定总价520万元，定金20万元。签约后一个月内小区同类房源挂牌价上涨约8%，王某以“配偶未签字同意”为由主张合同无效并拒绝过户。");
        List<Map<String, Object>> c2chs = new ArrayList<>();
        c2chs.add(chapter("ch201", "争议焦点（待展开）", "无权处分/夫妻共同财产的审查；继续履行与定金罚则能否并用。", 1));
        c2.put("chapters", c2chs);
        List<Map<String, Object>> c2notes = new ArrayList<>();
        c2notes.add(note("n203", "下周讲课用，记得补《民法典》第597条与买卖合同司法解释相关条文。", "2026-09-20 16:40:00"));
        c2.put("notes", c2notes);
        c2.put("createdAt", "2026-09-19 11:30:00");
        c2.put("updatedAt", "2026-09-20 16:40:00");
        list.add(c2);

        // ---------- 案例 3：已发布，精简内容 ----------
        Map<String, Object> c3 = new LinkedHashMap<>();
        c3.put("id", "c003");
        c3.put("title", "赵某机动车交通事故责任纠纷案");
        c3.put("caseNumber", "（2025）粤0305民初3321号");
        c3.put("court", "深圳市南山区人民法院");
        c3.put("status", "published");
        c3.put("summary", "机动车与非机动车发生交通事故，非机动车驾驶人受伤，争议在于交强险、商业三者险的赔付顺序及超出部分的责任比例。");
        c3.put("facts", "2025年1月，赵某驾驶小型轿车转弯时与骑行电动自行车直行的钱某发生碰撞，钱某受伤，交警认定赵某转弯未让直行，负事故主要责任，钱某超速负次要责任。钱某产生医疗费等损失共计18万元。");
        List<Map<String, Object>> c3chs = new ArrayList<>();
        c3chs.add(chapter("ch301", "责任承担顺序",
                "1. 先由承保交强险的保险公司在责任限额内赔偿；\n2. 不足部分由商业三者险按合同约定赔偿；\n3. 仍有不足的，由侵权人按责任比例承担。", 1));
        c3.put("chapters", c3chs);
        c3.put("notes", new ArrayList<Map<String, Object>>());
        c3.put("createdAt", "2026-09-22 08:30:00");
        c3.put("updatedAt", "2026-09-23 10:10:00");
        list.add(c3);

        return list;
    }

    private static Map<String, Object> chapter(String id, String heading, String body, int order) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("heading", heading);
        m.put("body", body);
        m.put("order", order);
        return m;
    }

    private static Map<String, Object> note(String id, String content, String createdAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("content", content);
        m.put("createdAt", createdAt);
        return m;
    }
}
