package com.fc.v2.mapper.auto;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fc.v2.model.auto.TOadsGovCaseReport;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 监管办件报备登记数据层（正式立起来之前那一小段；撤销不进主档）。
 *
 * @author fuce
 * @date 2026-10-06
 */
public interface TOadsGovCaseReportMapper extends BaseMapper<TOadsGovCaseReport> {

    /** 按报备幂等请求号认出头一笔（同一批报备两回认成同一件）。 */
    @Select("SELECT * FROM t_oads_gov_case_report WHERE req_no = #{reqNo} LIMIT 1")
    TOadsGovCaseReport selectByReqNo(@Param("reqNo") String reqNo);

    /** 同一点位是否已有报备中那一条（一处点位一次只一条流程在报备/在办）。 */
    @Select("SELECT * FROM t_oads_gov_case_report WHERE open_token = #{spotNo} AND del_flag = 0 LIMIT 1")
    TOadsGovCaseReport selectOpenBySpot(@Param("spotNo") String spotNo);
}
