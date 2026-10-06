-- oads 监管办件四步流程 —— schema 扩展（chen-026）
-- 一处在册点位一条办件：申报 → 核准 → 设置 → 验收，先后不可调换。
-- 本套与旧的 t_oads_gov_flow（state-machine 骨架）并存：旧表不添列、不改名，
-- 四步裁决由报备登记 + 办件本 + 步序留痕这一套等价承接。
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_026

-- ---------------------------------------------------------------- 报备登记（正式立起来之前那一小段）
-- 撤销只可能出现在这一小段；被撤掉的报备不进办件主档、也不占“尚未走完”的位置。
-- open_token 在报备期间等于点位编码（同点位只许一条未立/在途），立件或撤销后置空，
-- 唯一索引允许多个 NULL，故终态不挡后续同点位再报。
CREATE TABLE IF NOT EXISTS t_oads_gov_case_report (
  id bigint NOT NULL COMMENT '主键',
  req_no varchar(64) DEFAULT NULL COMMENT '报备幂等请求号（同一批报备两回凭它认成同一件，可空）',
  spot_no varchar(64) NOT NULL COMMENT '点位编码（四项输入之一）',
  area_no varchar(64) NOT NULL COMMENT '所属路段代号（四项输入之一）',
  qty int NOT NULL COMMENT '处数（四项输入之一，整数）',
  check_score decimal(12,3) NOT NULL COMMENT '拟判定的校核分值（四项输入之一，0..100）',
  status int NOT NULL DEFAULT '0' COMMENT '报备情形 0报备中 1已正式立起 6立前撤销（不进主档）',
  case_id bigint DEFAULT NULL COMMENT '正式立起后对应的办件本主键',
  open_token varchar(128) DEFAULT NULL COMMENT '报备占用令牌=点位编码；立起/撤销置空',
  cancel_note varchar(500) DEFAULT NULL COMMENT '立前撤销交代（原文）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_gov_report_req (req_no),
  UNIQUE KEY uk_gov_report_open (open_token),
  KEY idx_gov_report_spot (spot_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='监管办件报备登记（立前那一小段，撤销不进主档）';

-- ---------------------------------------------------------------- 监管办件本（正式立起才进这张）
-- 立一条加一、封/收一条减一：未走完只数 status=1。封存(2)/撤回报备(3)/划转(4)/作废(5)照常列名。
-- step_no 只作服务端按留痕重数后的落账与回显：停在哪一步由系统按
-- t_oads_gov_case_flow_log 的在档 REACH 自己数，报文里带的步次一律只作参考、不作记账依据。
-- 同一点位任意时刻只许一条在办：live_token 在办期间等于点位编码，收尾置空，唯一索引兜底。
CREATE TABLE IF NOT EXISTS t_oads_gov_case (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) NOT NULL COMMENT '监管办件单号（正式立起时服务端发）',
  report_id bigint DEFAULT NULL COMMENT '来源报备登记主键（办件凭报备立起）',
  spot_no varchar(64) NOT NULL COMMENT '点位编码（跟着一处在册点位走）',
  area_no varchar(64) NOT NULL COMMENT '所属路段代号（划段以目录为准）',
  qty int NOT NULL COMMENT '处数（报备四项之一）',
  check_score decimal(12,3) NOT NULL COMMENT '拟判定的校核分值（报备四项之一，0..100）',
  step_no int DEFAULT NULL COMMENT '当前步 1申报 2核准 3设置 4验收（服务端按留痕重数落账，报文步次不采信）',
  status int NOT NULL DEFAULT '1' COMMENT '情形 1在办 2已封存(验收走完) 3申报后撤回报备 4路段划归邻段 5作废',
  setup_accept int DEFAULT '0' COMMENT '设置一步核收是否记为同意 0未记同意 1已记同意（未同意不得进验收）',
  witness_count int DEFAULT '0' COMMENT '验收本轮两名见证岗已落笔数（按留痕重数，屏上仅回显）',
  recheck_voucher varchar(64) DEFAULT NULL COMMENT '复验凭证编码（验收回写本行；与两名见证齐才算走完）',
  closed_kind varchar(16) DEFAULT NULL COMMENT '收尾种类 WITHDRAW撤回报备 REASSIGN划归邻段 VOID作废',
  sealed_at datetime(3) DEFAULT NULL COMMENT '验收走完封存那一刻',
  closed_at datetime(3) DEFAULT NULL COMMENT '收尾达成那一刻（撤回/划转/作废）',
  live_token varchar(128) DEFAULT NULL COMMENT '在途去重令牌 在办期间=点位编码，收尾置空；同点位只一条在办',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_gov_case_biz_no (biz_no),
  UNIQUE KEY uk_gov_case_live_token (live_token),
  KEY idx_gov_case_spot (spot_no),
  KEY idx_gov_case_area (area_no),
  KEY idx_gov_case_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='监管办件本（一处点位一条，四步服务端裁决）';

-- ---------------------------------------------------------------- 办件步序留痕
-- 一次送意见/一个动作一行，只许追加，不 update 内容、不物理删除。
-- 后退只把被退那一步当轮在档行 active 置 0（行保留在时序尾部，重走另起一轮，从上一轮之后重新累计）。
-- 走到验收且两名见证岗各一笔、复验凭证回写齐备才封存；封存后这张件可查不可写。
-- req_no 为并发幂等请求号：同一批并着办两回，第二回凭它认出是同一笔，不另插一条、也不丢一条。
CREATE TABLE IF NOT EXISTS t_oads_gov_case_flow_log (
  id bigint NOT NULL COMMENT '主键',
  case_id bigint NOT NULL COMMENT '哪一条监管办件',
  biz_no varchar(64) DEFAULT NULL COMMENT '办件单号（落账当时冗余）',
  step_no int NOT NULL COMMENT '步序 1申报 2核准 3设置 4验收（服务端点出，报文自称不作数）',
  round_no int NOT NULL COMMENT '该步轮次 后退一次加一轮 老轮留 active=0 留在时序尾部',
  seq_no int NOT NULL COMMENT '本件内笔序（逐笔累加，同一时刻两通道各一笔也不并笔、不丢笔）',
  action varchar(16) NOT NULL COMMENT '动作 REACH走到 OPINION送意见 WITNESS验收见证 RETURN后退 SEAL封存 CLOSE收尾',
  note varchar(500) DEFAULT NULL COMMENT '这一步这句批语/交代（原文，后退不抹）',
  witness_post varchar(64) DEFAULT NULL COMMENT '见证岗代号（仅 WITNESS：两名见证岗各落一笔）',
  agree int DEFAULT NULL COMMENT '本笔判断 1同意 2不同意（设置核收意见落这里）',
  operator varchar(64) DEFAULT NULL COMMENT '经办岗（只记是谁，步不由他点）',
  req_no varchar(64) DEFAULT NULL COMMENT '并发幂等请求号（同号第二回认出是同一笔，不重复落）',
  active int DEFAULT '1' COMMENT '本轮是否在档 1当前轮 0随后退退出（不删行，继续可查）',
  acted_at datetime(3) NOT NULL COMMENT '落账时刻（毫秒；同秒两笔仍为两条，看 seq 排先后）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_gov_case_req_no (req_no),
  KEY idx_gov_flow_case (case_id, active),
  KEY idx_gov_flow_step (case_id, step_no, round_no, active),
  KEY idx_gov_flow_acted (acted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='监管办件步序留痕（append-only）';

-- ---------------------------------------------------------------- 菜单与权限（幂等，已存在同 id 则跳过）
-- 权限码与 OadsGovCaseController 上的 @RequiresPermissions 逐字对齐。
INSERT INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
SELECT 9400, '监管办件', '一处点位一条办件：申报/核准/设置/验收四步', '/oadsGovCase/view', 0, 0, 'oads:oadsGovCase:view', 1, 'layui-icon-snowflake', 94, 0, 'seed', NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_sys_permission WHERE id = 9400);
INSERT INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
SELECT 9401, '办件台账', '在办/封存/收尾照常出册，封存行只给查不给操作', '/oadsGovCase/list', 0, 9400, 'oads:oadsGovCase:list', 2, '', NULL, 0, 'seed', NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_sys_permission WHERE id = 9401);
INSERT INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
SELECT 9402, '报备', '四项输入齐才收报备；同点位只一条；报备后可立前撤销', '/oadsGovCase/report', 0, 9400, 'oads:oadsGovCase:create', 2, 'entypo-plus-squared', NULL, 0, 'seed', NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_sys_permission WHERE id = 9402);
INSERT INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
SELECT 9410, '正式立件', '报备四项齐后正式立起来，落第一步申报、计入尚未走完', '/oadsGovCase/establish', 0, 9400, 'oads:oadsGovCase:create', 2, 'fa fa-check-square-o', NULL, 0, 'seed', NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_sys_permission WHERE id = 9410);
INSERT INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
SELECT 9411, '立前撤销', '只在正式立起来之前那一小段；不进主档、不占尚未走完名额', '/oadsGovCase/cancel', 0, 9400, 'oads:oadsGovCase:close', 2, 'fa fa-times', NULL, 0, 'seed', NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_sys_permission WHERE id = 9411);
INSERT INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
SELECT 9403, '送意见/见证', '当前步送意见（首条为准）；验收两见证岗各一笔并回写凭证', '/oadsGovCase/opinion', 0, 9400, 'oads:oadsGovCase:opinion', 2, 'fa fa-pencil', NULL, 0, 'seed', NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_sys_permission WHERE id = 9403);
INSERT INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
SELECT 9404, '推进一步', '只跨紧邻下一步，准入不过不进；跨步不生效', '/oadsGovCase/advance', 0, 9400, 'oads:oadsGovCase:flow', 2, 'fa fa-arrow-right', NULL, 0, 'seed', NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_sys_permission WHERE id = 9404);
INSERT INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
SELECT 9405, '后退一步', '退一步当轮转留痕尾部，重走重新累计；第一步不退', '/oadsGovCase/returnStep', 0, 9400, 'oads:oadsGovCase:flow', 2, 'fa fa-arrow-left', NULL, 0, 'seed', NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_sys_permission WHERE id = 9405);
INSERT INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
SELECT 9406, '撤回报备', '申报之后撤回报备即收尾，不必再往下走', '/oadsGovCase/withdraw', 0, 9400, 'oads:oadsGovCase:close', 2, 'fa fa-undo', NULL, 0, 'seed', NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_sys_permission WHERE id = 9406);
INSERT INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
SELECT 9407, '划段收尾', '所属路段从目录划归邻段，本件即收尾', '/oadsGovCase/reassignClose', 0, 9400, 'oads:oadsGovCase:close', 2, 'fa fa-exchange', NULL, 0, 'seed', NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_sys_permission WHERE id = 9407);
INSERT INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
SELECT 9408, '作废', '作废只从最后一步（验收）发起', '/oadsGovCase/void', 0, 9400, 'oads:oadsGovCase:void', 2, 'fa fa-ban', NULL, 0, 'seed', NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_sys_permission WHERE id = 9408);
INSERT INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
SELECT 9409, '台账对账', '账面步次/未走完数与留痕重算同一次核对', '/oadsGovCase/reconcile', 0, 9400, 'oads:oadsGovCase:list', 2, 'fa fa-balance-scale', NULL, 0, 'seed', NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_sys_permission WHERE id = 9409);
