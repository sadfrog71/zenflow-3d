package com.deepdimen.matrix.knowledge.excel;

import cn.idev.excel.write.handler.CellWriteHandler;
import cn.idev.excel.write.handler.SheetWriteHandler;
import cn.idev.excel.write.handler.context.CellWriteHandlerContext;
import cn.idev.excel.write.handler.context.SheetWriteHandlerContext;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;

import java.util.HashMap;
import java.util.Map;

/**
 * AI问答明细Excel样式处理器
 */
public class AiChatDetailExcelStyleHandler implements CellWriteHandler, SheetWriteHandler {

	private final Map<String, CellStyle> styleCache = new HashMap<>();

	@Override
	public void afterSheetCreate(SheetWriteHandlerContext context) {
		Sheet sheet = context.getWriteSheetHolder().getSheet();
		sheet.createFreezePane(0, 1);
		sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, 12));
		int[] widths = { 20, 16, 12, 16, 20, 50, 16, 18, 16, 16, 14, 32, 32 };
		for (int i = 0; i < widths.length; i++) {
			sheet.setColumnWidth(i, widths[i] * 256);
		}
	}

	@Override
	public void afterCellDispose(CellWriteHandlerContext context) {
		Cell cell = context.getCell();
		boolean head = Boolean.TRUE.equals(context.getHead());
		if (head) {
			cell.getRow().setHeightInPoints(24);
			cell.setCellStyle(getHeadStyle(cell.getSheet().getWorkbook()));
			return;
		}
		cell.getRow().setHeightInPoints(22);
		cell.setCellStyle(getDataStyle(cell));
	}

	private CellStyle getHeadStyle(Workbook workbook) {
		return styleCache.computeIfAbsent("head", key -> {
			CellStyle style = createBaseStyle(workbook, IndexedColors.DARK_BLUE, IndexedColors.WHITE);
			Font font = workbook.createFont();
			font.setBold(true);
			font.setColor(IndexedColors.WHITE.getIndex());
			style.setFont(font);
			style.setAlignment(HorizontalAlignment.CENTER);
			return style;
		});
	}

	private CellStyle getDataStyle(Cell cell) {
		boolean alternate = cell.getRowIndex() % 2 == 0;
		String semantic = semantic(cell);
		String key = (alternate ? "alternate" : "normal") + semantic + (isNumeric(cell) ? "Numeric" : "Text");
		return styleCache.computeIfAbsent(key, ignored -> {
			IndexedColors background = semanticBackground(semantic, alternate);
			CellStyle style = createBaseStyle(cell.getSheet().getWorkbook(), background, IndexedColors.BLACK);
			style.setWrapText(cell.getColumnIndex() == 5);
			style.setAlignment(isNumeric(cell) ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT);
			if (isNumeric(cell)) {
				style.setDataFormat(cell.getSheet().getWorkbook().createDataFormat().getFormat("#,##0"));
			}
			return style;
		});
	}

	private CellStyle createBaseStyle(Workbook workbook, IndexedColors background, IndexedColors foreground) {
		CellStyle style = workbook.createCellStyle();
		style.setFillForegroundColor(background.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		style.setVerticalAlignment(VerticalAlignment.CENTER);
		style.setBorderTop(BorderStyle.THIN);
		style.setBorderBottom(BorderStyle.THIN);
		style.setBorderLeft(BorderStyle.THIN);
		style.setBorderRight(BorderStyle.THIN);
		style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
		style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
		style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
		style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
		if (foreground != IndexedColors.BLACK) {
			Font font = workbook.createFont();
			font.setColor(foreground.getIndex());
			style.setFont(font);
		}
		return style;
	}

	private String semantic(Cell cell) {
		if (cell.getColumnIndex() != 6 && cell.getColumnIndex() != 10) {
			return "";
		}
		return switch (cell.getStringCellValue()) {
			case "已完成", "正向" -> "Success";
			case "处理失败", "负向" -> "Danger";
			case "用量受限" -> "Warning";
			case "敏感词拦截" -> "Sensitive";
			default -> "Muted";
		};
	}

	private IndexedColors semanticBackground(String semantic, boolean alternate) {
		return switch (semantic) {
			case "Success" -> IndexedColors.LIGHT_GREEN;
			case "Danger" -> IndexedColors.ROSE;
			case "Warning" -> IndexedColors.LIGHT_ORANGE;
			case "Sensitive" -> IndexedColors.LIGHT_YELLOW;
			case "Muted" -> IndexedColors.GREY_25_PERCENT;
			default -> alternate ? IndexedColors.PALE_BLUE : IndexedColors.WHITE;
		};
	}

	private boolean isNumeric(Cell cell) {
		return cell.getColumnIndex() >= 7 && cell.getColumnIndex() <= 9;
	}

}
