package com.lawcase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 首次启动（数据文件不存在）时写入的示例数据。 */
public final class Seed {
    private Seed() {}

    public static Map<String, Object> sample() {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Object> cases = new ArrayList<>();
        cases.add(contractCase());
        cases.add(tortCase());
        root.put("cases", cases);
        return root;
    }

    private static Map<String, Object> contractCase() {
        Map<String, Object> c = base("c001",
                "张某诉某科技公司买卖合同纠纷案",
                "（2025）京 0105 民初 1234 号",
                "北京市朝阳区人民法院", "2025-03-12", "合同纠纷",
                "被告向原告采购办公设备后逾期付款，原告主张货款及违约金，被告以设备存在质量问题抗辩。",
                "2025 年 1 月，原告张某与被告某科技公司签订《办公设备采购合同》，约定原告向被告供应电脑及配套设备，总价 18 万元，货到验收后 15 日内付清。原告于 1 月 20 日交付全部货物，被告员工签收。被告以其中三台电脑存在蓝屏故障为由，一直未付款。原告遂诉至法院，要求支付货款 18 万元及逾期付款违约金。",
                "published", "2025-09-01T09:00:00Z", "2025-09-20T14:30:00Z");

        c.put("chapters", list(
                chapter("ch001", 1, "一、案件事实梳理",
                        "1. 合同成立：双方于 2025 年 1 月签订书面采购合同，合同合法有效。\n2. 履行情况：原告已按期交货，被告员工签收确认。\n3. 争议焦点：被告主张三台电脑存在质量问题，但未在约定检验期内提出书面异议。",
                        "published", "2025-09-01T09:10:00Z", "2025-09-18T10:00:00Z"),
                chapter("ch002", 2, "二、争议焦点与法律适用",
                        "焦点一：被告关于质量问题的抗辩能否成立？\n依据《民法典》第六百二十一条，买受人未在约定检验期限内通知出卖人的，视为标的物数量或质量符合约定。\n焦点二：违约金如何计算？\n合同约定按日万分之五计算，被告可请求法院对过分高于损失的部分予以调减。",
                        "published", "2025-09-01T09:20:00Z", "2025-09-19T11:00:00Z"),
                chapter("ch003", 3, "三、裁判要旨与课堂讨论",
                        "法院认定被告未在检验期内提出质量异议，抗辩不成立，应支付货款；违约金调整为按 LPR 的 1.5 倍计算。\n讨论：若合同未约定检验期限，裁判结果会有何不同？（《民法典》第六百二十一条第二款）",
                        "draft", "2025-09-20T14:00:00Z", "2025-09-20T14:30:00Z")
        ));
        c.put("notes", list(
                note("n001", "课堂提问安排：先让学生阅读判决书第 3-5 页，再分组讨论 10 分钟；提醒学生区分『检验期限』与『质量保证期』。",
                        "2025-09-19T08:00:00Z", "2025-09-19T08:00:00Z")
        ));
        return c;
    }

    private static Map<String, Object> tortCase() {
        Map<String, Object> c = base("c002",
                "李某诉王某饲养动物损害责任案（备课草稿）",
                "（2025）沪 0115 民初 88 号",
                "上海市浦东新区人民法院", "2025-05-08", "侵权责任",
                "居民小区内未拴绳犬只咬伤路人，饲养人以受害人曾逗弄犬只为由主张减轻责任。",
                "2025 年 4 月某日，王某在小区草坪遛狗时未拴绳，李某路过时被犬只咬伤小腿，花费医疗费 6000 余元。王某称李某此前曾用树枝逗弄该犬，自身存在过错。",
                "draft", "2025-09-22T10:00:00Z", "2025-09-22T10:00:00Z");
        c.put("chapters", list(
                chapter("ch004", 1, "一、规范依据",
                        "《民法典》第一千二百四十六条：未对动物采取安全措施造成他人损害的，动物饲养人或者管理人应当承担侵权责任。",
                        "draft", "2025-09-22T10:10:00Z", "2025-09-22T10:10:00Z")
        ));
        c.put("notes", list(
                note("n002", "待补充：调取小区监控的证据规则；下节课前补完『举证责任』一节。",
                        "2025-09-22T10:15:00Z", "2025-09-22T10:15:00Z")
        ));
        return c;
    }

    private static Map<String, Object> base(String id, String title, String caseNo, String court,
                                           String date, String category, String summary, String facts,
                                           String status, String createdAt, String updatedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("caseNo", caseNo);
        m.put("court", court);
        m.put("date", date);
        m.put("category", category);
        m.put("summary", summary);
        m.put("facts", facts);
        m.put("status", status);
        m.put("createdAt", createdAt);
        m.put("updatedAt", updatedAt);
        return m;
    }

    private static Map<String, Object> chapter(String id, int order, String title, String content,
                                               String status, String createdAt, String updatedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("order", order);
        m.put("title", title);
        m.put("content", content);
        m.put("status", status);
        m.put("createdAt", createdAt);
        m.put("updatedAt", updatedAt);
        return m;
    }

    private static Map<String, Object> note(String id, String content, String createdAt, String updatedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("content", content);
        m.put("createdAt", createdAt);
        m.put("updatedAt", updatedAt);
        return m;
    }

    private static List<Object> list(Object... items) {
        List<Object> l = new ArrayList<>();
        for (Object o : items) l.add(o);
        return l;
    }
}
