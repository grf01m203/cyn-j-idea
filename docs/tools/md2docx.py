# -*- coding: utf-8 -*-
"""把 架构设计规范文档.md 转换为排版良好的 .docx（标题/表格/代码块/列表/引用/目录/页脚页码）。"""
import re
import sys
import os

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

HERE = os.path.dirname(os.path.abspath(__file__))
MD = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, '..','架构设计规范文档.md')
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, '..','架构设计规范文档.docx')
IMG = sys.argv[3] if len(sys.argv) > 3 else ''
MD = os.path.abspath(MD); OUT = os.path.abspath(OUT)

HEADING_MAP = {2: 1, 3: 2, 4: 3, 5: 4}   # md level -> docx heading level


def set_east_asia(run_or_style, font_name):
    el = run_or_style.element if hasattr(run_or_style, 'element') else run_or_style._element
    rpr = el.get_or_add_rPr() if hasattr(el, 'get_or_add_rPr') else None
    if rpr is None:
        return
    rfonts = rpr.find(qn('w:rFonts'))
    if rfonts is None:
        rfonts = OxmlElement('w:rFonts')
        rpr.append(rfonts)
    rfonts.set(qn('w:eastAsia'), font_name)


def style_font(style, name, east, size=None, bold=None, color=None):
    style.font.name = name
    set_east_asia(style, east)
    if size is not None:
        style.font.size = Pt(size)
    if bold is not None:
        style.font.bold = bold
    if color is not None:
        style.font.color.rgb = RGBColor(*color)


def shade(el, fill):
    pr = el.get_or_add_tcPr() if el.tag == qn('w:tc') else el.get_or_add_pPr()
    shd = OxmlElement('w:shd')
    shd.set(qn('w:val'), 'clear')
    shd.set(qn('w:color'), 'auto')
    shd.set(qn('w:fill'), fill)
    pr.append(shd)


def add_field(paragraph, instr, placeholder=''):
    run = paragraph.add_run()
    fld_begin = OxmlElement('w:fldChar')
    fld_begin.set(qn('w:fldCharType'), 'begin')
    instr_el = OxmlElement('w:instrText')
    instr_el.set(qn('xml:space'), 'preserve')
    instr_el.text = instr
    fld_sep = OxmlElement('w:fldChar')
    fld_sep.set(qn('w:fldCharType'), 'separate')
    text_el = OxmlElement('w:t')
    text_el.text = placeholder
    fld_end = OxmlElement('w:fldChar')
    fld_end.set(qn('w:fldCharType'), 'end')
    run._r.append(fld_begin)
    run._r.append(instr_el)
    run._r.append(fld_sep)
    run._r.append(text_el)
    run._r.append(fld_end)


INLINE_RE = re.compile(r'(\*\*.+?\*\*|`[^`]+`|\[[^\]]+\]\([^)]+\))')


def add_inline(paragraph, text, base_size=None, code_size=None):
    parts = INLINE_RE.split(text)
    for part in parts:
        if not part:
            continue
        if part.startswith('**') and part.endswith('**') and len(part) > 4:
            r = paragraph.add_run(part[2:-2])
            r.bold = True
            if base_size:
                r.font.size = Pt(base_size)
        elif part.startswith('`') and part.endswith('`') and len(part) > 2:
            r = paragraph.add_run(part[1:-1])
            r.font.name = 'Consolas'
            set_east_asia(r, '宋体')
            r.font.size = Pt(code_size or (base_size - 0.5 if base_size else 9.5))
            r.font.color.rgb = RGBColor(0xA3, 0x15, 0x15)
        elif part.startswith('[') and '](' in part:
            m = re.match(r'\[([^\]]+)\]\(([^)]+)\)', part)
            r = paragraph.add_run(m.group(1))
            r.font.color.rgb = RGBColor(0x1F, 0x4E, 0x79)
            r.font.underline = True
            if base_size:
                r.font.size = Pt(base_size)
        else:
            r = paragraph.add_run(part)
            if base_size:
                r.font.size = Pt(base_size)


def split_row(line):
    s = line.strip()
    if s.startswith('|'):
        s = s[1:]
    if s.endswith('|'):
        s = s[:-1]
    return [c.strip() for c in s.split('|')]


def is_sep_row(cells):
    return all(re.fullmatch(r':?-{2,}:?', c or '') for c in cells if c is not None) and any(cells)


def main():
    with open(MD, 'r', encoding='utf-8') as f:
        lines = f.read().replace('\r\n', '\n').split('\n')

    doc = Document()
    # 页面：A4 + 页边距
    sec = doc.sections[0]
    sec.page_width, sec.page_height = Cm(21), Cm(29.7)
    sec.top_margin = sec.bottom_margin = Cm(2.3)
    sec.left_margin = sec.right_margin = Cm(2.4)

    # 样式
    style_font(doc.styles['Normal'], 'Times New Roman', '宋体', 10.5)
    pf = doc.styles['Normal'].paragraph_format
    pf.line_spacing = 1.25
    pf.space_after = Pt(4)

    style_font(doc.styles['Title'], '微软雅黑', '微软雅黑', 22, True, (0x1F, 0x38, 0x64))
    for lvl, size, color in ((1, 16, (0x1F, 0x38, 0x64)), (2, 13.5, (0x2E, 0x54, 0x96)), (3, 12, (0x33, 0x33, 0x33)), (4, 11, (0x33, 0x33, 0x33))):
        st = doc.styles['Heading %d' % lvl]
        style_font(st, '微软雅黑', '微软雅黑', size, True, color)
        st.paragraph_format.space_before = Pt(10 if lvl <= 2 else 6)
        st.paragraph_format.space_after = Pt(4)
        st.paragraph_format.keep_with_next = True

    code_st = doc.styles.add_style('CodeBlock', 1)
    code_st.base_style = doc.styles['Normal']
    style_font(code_st, 'Consolas', '宋体', 8.5)
    code_st.paragraph_format.line_spacing = 1.0
    code_st.paragraph_format.space_after = Pt(0)
    code_st.paragraph_format.space_before = Pt(0)
    code_st.paragraph_format.left_indent = Cm(0.25)

    quote_st = doc.styles.add_style('QuoteBlock', 1)
    quote_st.base_style = doc.styles['Normal']
    style_font(quote_st, '楷体', '楷体', 10)
    quote_st.font.color.rgb = RGBColor(0x55, 0x55, 0x55)
    quote_st.paragraph_format.left_indent = Cm(0.4)
    quote_st.paragraph_format.space_before = Pt(2)
    quote_st.paragraph_format.space_after = Pt(2)

    # 页眉页脚
    hp = sec.header.paragraphs[0]
    hp.text = '校园场馆预约与拼场组队系统 · 架构设计规范文档'
    hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    for r in hp.runs:
        r.font.size = Pt(8)
        r.font.color.rgb = RGBColor(0x80, 0x80, 0x80)
        set_east_asia(r, '宋体')
    fp = sec.footer.paragraphs[0]
    fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = fp.add_run('第 ')
    r.font.size = Pt(9)
    add_field(fp, 'PAGE', '1')
    r2 = fp.add_run(' 页 / 共 ')
    r2.font.size = Pt(9)
    add_field(fp, 'NUMPAGES', '1')
    r3 = fp.add_run(' 页')
    r3.font.size = Pt(9)

    # 标题（第一行 H1）
    title_done = False
    toc_pending = False
    i = 0
    in_code = False
    code_buf = []
    para_buf = []
    table = None

    def flush_para():
        nonlocal para_buf
        if para_buf:
            text = ''.join(para_buf).strip()
            para_buf = []
            if text:
                p = doc.add_paragraph()
                add_inline(p, text)
                if text.startswith('文档版本') or text.startswith('更新日期'):
                    p.alignment = WD_ALIGN_PARAGRAPH.CENTER

    def flush_table():
        nonlocal table
        if table and table[0]:
            header = table[0]
            rows = table[1:]
            ncol = len(header)
            t = doc.add_table(rows=0, cols=ncol)
            t.style = 'Table Grid'
            t.alignment = WD_TABLE_ALIGNMENT.CENTER
            t.autofit = False
            # 按内容长度分配列宽（CJK 记 2 个字符宽）
            def _w(s):
                s = re.sub(r'[`*_]', '', s or '')
                return sum(2 if '\u4e00' <= ch <= '\u9fff' or ch in '，、；：“”（）《》【】' else 1 for ch in s)
            weights = []
            for ci in range(ncol):
                mx = 0
                for row in [header] + rows:
                    mx = max(mx, _w(row[ci] if ci < len(row) else ''))
                weights.append(max(6, min(mx, 34)))
            total = float(sum(weights))
            avail = 16.2
            widths = [max(2.0, avail * w / total) for w in weights]
            s = sum(widths)
            widths = [w * avail / s for w in widths]
            hr = t.add_row().cells
            for ci, cell_text in enumerate(header[:ncol]):
                hr[ci].width = Cm(widths[ci])
                p = hr[ci].paragraphs[0]
                p.paragraph_format.space_after = Pt(0)
                add_inline(p, cell_text, 9)
                for r in p.runs:
                    r.bold = True
                shade(hr[ci]._tc, 'D9E2F3')
            for row in rows:
                cells = t.add_row().cells
                for ci in range(ncol):
                    cells[ci].width = Cm(widths[ci])
                    cells[ci].vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
                    txt = row[ci] if ci < len(row) else ''
                    p = cells[ci].paragraphs[0]
                    p.paragraph_format.space_after = Pt(0)
                    p.paragraph_format.line_spacing = 1.1
                    add_inline(p, txt, 9)
            # 表后留一点空
            sp = doc.add_paragraph()
            sp.paragraph_format.space_after = Pt(2)
            sp.paragraph_format.line_spacing = 1.0
        table = None

    def flush_code():
        nonlocal code_buf
        if code_buf:
            p = doc.add_paragraph(style='CodeBlock')
            shade(p._p, 'F5F5F5')
            p.add_run('\n'.join(code_buf))
            code_buf = []
            sp = doc.add_paragraph()
            sp.paragraph_format.space_after = Pt(2)
            sp.paragraph_format.line_spacing = 1.0

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        if not in_code and stripped.startswith('```'):
            flush_para()
            flush_table()
            in_code = True
            i += 1
            continue
        if in_code:
            if stripped.startswith('```'):
                in_code = False
                flush_code()
            else:
                code_buf.append(line.rstrip())
            i += 1
            continue

        if not stripped:
            flush_para()
            flush_table()
            i += 1
            continue

        m = re.match(r'^(#{1,6})\s+(.*)$', line)
        if m:
            flush_para()
            flush_table()
            level = len(m.group(1))
            text = m.group(2).strip()
            if level == 1 and not title_done:
                tp = doc.add_paragraph(style='Title')
                tp.alignment = WD_ALIGN_PARAGRAPH.CENTER
                add_inline(tp, text, 22)
                title_done = True
                toc_pending = True
            else:
                if toc_pending:
                    th = doc.add_paragraph()
                    th.alignment = WD_ALIGN_PARAGRAPH.CENTER
                    th.paragraph_format.page_break_before = True
                    th.paragraph_format.space_before = Pt(0)
                    thr = th.add_run('目  录')
                    thr.bold = True
                    thr.font.size = Pt(16)
                    thr.font.name = '微软雅黑'
                    set_east_asia(thr, '微软雅黑')
                    tp2 = doc.add_paragraph()
                    add_field(tp2, ' TOC \\o "1-3" \\h \\z \\u ', '（在 Word/WPS 中右键“更新域”生成目录）')
                    hint = doc.add_paragraph()
                    hr2 = hint.add_run('提示：打开文档后按 F9，或在目录处右键选择“更新域”→“更新整个目录”，即可生成带页码的目录。')
                    hr2.font.size = Pt(8.5)
                    hr2.font.color.rgb = RGBColor(0x80, 0x80, 0x80)
                    hint.paragraph_format.space_before = Pt(2)
                    toc_pending = False
                lvl = HEADING_MAP.get(level, 4)
                hp2 = doc.add_paragraph(style='Heading %d' % lvl)
                if lvl == 1:
                    hp2.paragraph_format.page_break_before = True
                add_inline(hp2, text)
            i += 1
            continue

        if stripped.startswith('|'):
            flush_para()
            cells = split_row(line)
            if is_sep_row(cells):
                i += 1
                continue
            if table is None:
                table = [cells]
            else:
                table.append(cells)
            i += 1
            continue
        else:
            flush_table()

        if stripped.startswith('>'):
            flush_para()
            p = doc.add_paragraph(style='QuoteBlock')
            add_inline(p, stripped[1:].strip(), 10)
            i += 1
            continue

        if re.fullmatch(r'-{3,}|\*{3,}|_{3,}', stripped):
            flush_para()
            i += 1
            continue

        lm = re.match(r'^(\s*)([-*+]|\d+\.)\s+(.*)$', line)
        if lm:
            flush_para()
            indent = len(lm.group(1).replace('\t', '  '))
            depth = min(indent // 2, 2)
            marker = lm.group(2)
            text = lm.group(3).strip()
            if re.fullmatch(r'\d+\.', marker):
                # 手写序号，避免 Word 列表样式跨列表续号
                p = doc.add_paragraph()
                pf = p.paragraph_format
                pf.left_indent = Cm(0.6 + 0.6 * depth)
                pf.first_line_indent = Cm(-0.6)
                pf.space_after = Pt(2)
                pf.line_spacing = 1.35
                p.add_run(marker + '  ')
                add_inline(p, text)
                i += 1
                continue
            style_name = 'List Bullet' if depth == 0 else ('List Bullet 2' if depth == 1 else 'List Bullet 3')
            try:
                p = doc.add_paragraph(style=style_name)
            except KeyError:
                p = doc.add_paragraph()
                p.paragraph_format.left_indent = Cm(0.6 + 0.6 * depth)
                p.add_run('• ')
            p.paragraph_format.space_after = Pt(2)
            add_inline(p, text)
            i += 1
            continue

        # 普通段落：吸收后续非空、非结构行
        para_buf.append(stripped)
        i += 1

    flush_para()
    flush_table()
    flush_code()

    # 架构图：插到 §4 总体架构 首个正文段落之后
    if IMG and os.path.exists(IMG):
        target = None
        for p in doc.paragraphs:
            if p.style.name == 'Heading 1' and p.text.strip().startswith('4.'):
                target = p
                break
        if target is not None:
            cap = doc.add_paragraph()
            cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
            run = cap.add_run()
            run.add_picture(IMG, width=Cm(15.5))
            cp = doc.add_paragraph('图 4-1 总体架构与部署拓扑（示意）')
            cp.alignment = WD_ALIGN_PARAGRAPH.CENTER
            for r in cp.runs:
                r.font.size = Pt(9)
                r.font.color.rgb = RGBColor(0x59, 0x59, 0x59)
            target._p.addnext(cap._p)
            cap._p.addnext(cp._p)

    props = doc.core_properties
    props.title = '校园场馆预约与拼场组队系统 —— 架构设计规范文档'
    props.subject = '架构设计规范（17 章 + 附录），与 v0.4.2 实现同步'
    props.author = '陈以诺'
    props.comments = '由架构设计规范文档.md 重新导出；文档内容以 md 为准。'

    doc.save(OUT)
    print('saved:', OUT)
    print('paragraphs:', len(doc.paragraphs), 'tables:', len(doc.tables))


if __name__ == '__main__':
    main()
