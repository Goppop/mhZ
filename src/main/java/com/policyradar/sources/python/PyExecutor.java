package com.policyradar.sources.python;

import com.policyradar.sources.RawDoc;

import java.util.List;

/**
 * Python 执行器接口
 *
 * 定义 Python 脚本执行的抽象接口
 * 支持多种实现：subprocess（本地）、sidecar（远程）等
 */
public interface PyExecutor {

    /**
     * 执行 Python 脚本
     *
     * @param scriptPath 脚本路径
     * @param args 脚本参数
     * @param timeoutSec 超时时间
     * @return 原始文档列表
     */
    List<RawDoc> invoke(String scriptPath, List<String> args, int timeoutSec);
}