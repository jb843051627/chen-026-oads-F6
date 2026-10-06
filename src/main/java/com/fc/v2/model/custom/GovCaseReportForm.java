package com.fc.v2.model.custom;

import java.math.BigDecimal;

/**
 * 监管办件报备入参（正式立起来之前那一小段）。
 *
 * <p>建一条办件要四项输入：点位编码、所属路段代号、处数、拟判定的校核分值。
 * 缺任意一项都不收，由裁决处逐项点名缺哪一项，不丢一句“信息不全”。
 * 处数与分值按原文交来、服务端严格解析，不做截断、不代填。
 *
 * @author fuce
 * @date 2026-10-06
 */
public class GovCaseReportForm {

    /** 报备幂等请求号：同一批并着报备两回凭它认成同一件，可空（空则不做同号去重） */
    private String reqNo;

    /** 点位编码 */
    private String spotNo;

    /** 所属路段代号 */
    private String areaNo;

    /** 处数（原文，严格解析为正整数） */
    private String qtyText;

    /** 拟判定的校核分值（原文，严格解析为 0..100 的数） */
    private String checkScoreText;

    public String getReqNo() {
        return reqNo;
    }

    public void setReqNo(String reqNo) {
        this.reqNo = reqNo;
    }

    public String getSpotNo() {
        return spotNo;
    }

    public void setSpotNo(String spotNo) {
        this.spotNo = spotNo;
    }

    public String getAreaNo() {
        return areaNo;
    }

    public void setAreaNo(String areaNo) {
        this.areaNo = areaNo;
    }

    public String getQtyText() {
        return qtyText;
    }

    public void setQtyText(String qtyText) {
        this.qtyText = qtyText;
    }

    public String getCheckScoreText() {
        return checkScoreText;
    }

    public void setCheckScoreText(String checkScoreText) {
        this.checkScoreText = checkScoreText;
    }

    /** 处数严格解析：空/非整数返回 null，由裁决处区分“缺填”与“不是整数”。 */
    public static Integer parseQty(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return null;
        }
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        try {
            long v = Long.parseLong(t);
            if (v > Integer.MAX_VALUE) {
                return null;
            }
            return (int) v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 校核分值严格解析：空/非数返回 null；区间（0..100）由裁决处判。 */
    public static BigDecimal parseScore(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
