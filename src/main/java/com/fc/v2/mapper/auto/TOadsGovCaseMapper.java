package com.fc.v2.mapper.auto;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fc.v2.model.auto.TOadsGovCase;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 监管办件本数据层（正式立起才进这张表）。
 *
 * @author fuce
 * @date 2026-10-06
 */
public interface TOadsGovCaseMapper extends BaseMapper<TOadsGovCase> {

    /** 动作处理专用：行级锁住办件那一行，并发两笔串行记账，不并笔、不重判。 */
    @Select("SELECT * FROM t_oads_gov_case WHERE id = #{id} AND del_flag = 0 FOR UPDATE")
    TOadsGovCase selectByIdForUpdate(@Param("id") Long id);

    /** 按单号取。 */
    @Select("SELECT * FROM t_oads_gov_case WHERE biz_no = #{bizNo} AND del_flag = 0 LIMIT 1")
    TOadsGovCase selectByBizNo(@Param("bizNo") String bizNo);

    /** 按来源报备登记取（一条报备至多立成一条办件）。 */
    @Select("SELECT * FROM t_oads_gov_case WHERE report_id = #{reportId} AND del_flag = 0 LIMIT 1")
    TOadsGovCase selectByReportId(@Param("reportId") Long reportId);

    /** 同一点位是否已有一条在办：收尾后 live_token 置空，名额才腾出。 */
    @Select("SELECT * FROM t_oads_gov_case WHERE live_token = #{spotNo} AND del_flag = 0 LIMIT 1")
    TOadsGovCase selectOpenBySpot(@Param("spotNo") String spotNo);

    /**
     * 尚未走完数量：正式立起加一、封/收一条减一，只数在办 status=1 这一类。
     * 页面顶部只认这一次查询的结果，不取前端缓存，也不在别处另算一套。
     */
    @Select("SELECT COUNT(1) FROM t_oads_gov_case WHERE status = 1 AND del_flag = 0")
    long countOpen();

    /** 对账：全部正常办件逐件取，与留痕重算同一次核对。 */
    @Select("SELECT * FROM t_oads_gov_case WHERE del_flag = 0")
    List<TOadsGovCase> selectAllNormal();
}
