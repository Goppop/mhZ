#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Policy Radar Python SDK
让 Python 脚本可以轻松输出 JSON Lines 格式的数据
"""

import json
import sys
import hashlib
from datetime import date, datetime
from typing import Dict, List, Any, Optional


def emit(
    title: str,
    url: str,
    source: str,
    publish_date: Optional[date] = None,
    summary: Optional[str] = None,
    content: Optional[str] = None,
    issuing_agency: Optional[str] = None,
    document_number: Optional[str] = None,
    **extra: Any
) -> None:
    """
    输出一条原始文档数据（JSON Lines 格式）
    这是 Python 脚本与 Java 系统通信的唯一方式

    Args:
        title: 标题
        url: 原文链接
        source: 来源名称
        publish_date: 发布日期
        summary: 摘要
        content: 正文内容
        issuing_agency: 发布机构
        document_number: 文号
        **extra: 额外的元数据，会被放入 metadata 字段
    """
    # 构建文档对象
    doc: Dict[str, Any] = {
        "title": title,
        "url": url,
        "source": source,
        "publishDate": publish_date.isoformat() if publish_date else None,
        "summary": summary,
        "content": content,
        "issuingAgency": issuing_agency,
        "documentNumber": document_number,
        "metadata": extra if extra else None
    }

    # 输出 JSON
    json_str = json.dumps(doc, ensure_ascii=False)
    print(json_str, flush=True)


def log(message: str) -> None:
    """
    输出日志（会被重定向到 stderr，不影响正常的 JSON Lines 输出）

    Args:
        message: 日志消息
    """
    print(f"[LOG] {message}", file=sys.stderr, flush=True)


def warn(message: str) -> None:
    """
    输出警告日志

    Args:
        message: 警告消息
    """
    print(f"[WARN] {message}", file=sys.stderr, flush=True)


def error(message: str) -> None:
    """
    输出错误日志

    Args:
        message: 错误消息
    """
    print(f"[ERROR] {message}", file=sys.stderr, flush=True)


def debug(message: str) -> None:
    """
    输出调试日志

    Args:
        message: 调试消息
    """
    print(f"[DEBUG] {message}", file=sys.stderr, flush=True)


def parse_arguments() -> Dict[str, Any]:
    """
    解析命令行参数

    Returns:
        参数字典
    """
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--since", type=str, help="增量抓取的起始日期")
    parser.add_argument("--debug", action="store_true", help="启用调试模式")
    parser.add_argument("--task-id", type=int, help="任务日志 ID")

    args = parser.parse_args()
    return {
        "since": datetime.fromisoformat(args.since) if args.since else None,
        "debug": args.debug,
        "task_id": args.task_id
    }


def content_hash(title: str, publish_date: Optional[date] = None, content: str = "") -> str:
    """
    计算内容哈希值（用于去重）

    Args:
        title: 标题
        publish_date: 发布日期
        content: 内容

    Returns:
        SHA1 哈希字符串
    """
    data = f"{title}|{publish_date.isoformat() if publish_date else ''}|{content}"
    return hashlib.sha1(data.encode("utf-8")).hexdigest()