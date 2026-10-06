package com.fc.v2.mapper.auto;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fc.v2.model.auto.TOadsGovCaseFlowLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

/**
 * 监管办件步序留痕数据层（append-only：只 insert，不改内容，后退只抹 active）。
 *
 * @author fuce
 * @date 2026-10-06
 */
public interface TOadsGovCaseFlowLogMapper extends BaseMapper<TOadsGovCaseFlowLog> {

    /** 这一件全部留痕（含后退退出的老轮），按时序、笔序排，供追溯与系统重数停在哪一步。 */
    @Select("SELECT * FROM t_oads_gov_case_flow_log WHERE case_id = #{caseId} "
            + "ORDER BY acted_at ASC, seq_no ASC, id ASC")
    List<TOadsGovCaseFlowLog> listByCase(@Param("caseId") Long caseId);

    /** 本件本步本轮仍在档的落笔（走到/意见/见证），按时序、笔序排。 */
    @Select("SELECT * FROM t_oads_gov_case_flow_log "
            + "WHERE case_id = #{caseId} AND step_no = #{stepNo} AND round_no = #{roundNo} AND active = 1 "
            + "ORDER BY acted_at ASC, seq_no ASC, id ASC")
    List<TOadsGovCaseFlowLog> listActiveRound(@Param("caseId") Long caseId,
                                              @Param("stepNo") int stepNo,
                                              @Param("roundNo") int roundNo);

    /**
     * 后退一步：把被退那一步本轮在档的走到/意见/见证一并退出（active 置 0，行不删），
     * 它们按原先后留在时序尾部；此前各步与本步老轮一条不动。
     */
    @Update("UPDATE t_oads_gov_case_flow_log SET active = 0 "
            + "WHERE case_id = #{caseId} AND step_no = #{stepNo} AND round_no = #{roundNo} AND active = 1")
    int deactivateRound(@Param("caseId") Long caseId,
                        @Param("stepNo") int stepNo,
                        @Param("roundNo") int roundNo);

    /** 并发幂等：按请求号认出头一笔，同号第二回不重复落、也不当新笔丢开。 */
    @Select("SELECT * FROM t_oads_gov_case_flow_log WHERE req_no = #{reqNo} LIMIT 1")
    TOadsGovCaseFlowLog selectByReqNo(@Param("reqNo") String reqNo);

    /** 全部在档落笔，供只凭留痕逐件重算。 */
    @Select("SELECT * FROM t_oads_gov_case_flow_log WHERE active = 1")
    List<TOadsGovCaseFlowLog> listAllActive();

    /**
     * 当日受理流水：当日全部落笔（含后退退出的老轮，也算当日落过的一笔），
     * 按落账时刻、笔序排；同一时刻两条通道各一笔也不并笔。
     */
    @Select("SELECT * FROM t_oads_gov_case_flow_log "
            + "WHERE acted_at >= #{start} AND acted_at < #{end} "
            + "ORDER BY acted_at ASC, seq_no ASC, id ASC")
    List<TOadsGovCaseFlowLog> listLandedBetween(@Param("start") Date start, @Param("end") Date end);
}
