package id.tcs.validator;

import java.math.BigDecimal;

import org.adempiere.base.event.IEventTopics;
import org.compiere.model.MOrderLine;
import org.compiere.model.MRequisitionLine;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.osgi.service.event.Event;

import id.tcs.model.MMatchPR;
import id.tcs.model.X_M_MatchPR;
import id.tcs.model.X_M_MatchQuotation;

public class TCS_OrderLineValidator {
	public static String executeEvent(Event event, PO po) {
		String msg = "";
		MOrderLine orderLine = (MOrderLine) po;

		if (event.getTopic().equals(IEventTopics.PO_BEFORE_DELETE)) {
			if (orderLine.getC_Order().isSOTrx()) {
				msg += removeMatchQuotation(orderLine);
			} else {
				//Delete related Match PR before delete PO Line
				msg += removeMatchPR(orderLine);
			}
		}

		else if (event.getTopic().equals(IEventTopics.PO_BEFORE_CHANGE)) {
			if (!orderLine.getC_Order().isSOTrx()) {
				msg += ValidateMatchPR(orderLine);
			}
		}
		
		else if ((event.getTopic().equals(IEventTopics.PO_AFTER_NEW))) {
			if (!orderLine.getC_Order().isSOTrx()) {
				msg += createMatchPR(orderLine);
			}
		}
		
		else if (event.getTopic().equals(IEventTopics.PO_AFTER_CHANGE)) {
			if (!orderLine.getC_Order().isSOTrx()) {
				msg += updateMatchPR(orderLine);
			}
		}

		return msg;
	}

	private static String ValidateMatchPR(MOrderLine orderLine) {

		if (orderLine.get_ValueAsInt("M_RequisitionLine_ID") == 0)
			return "";
		
		//Update MatchPR according 
		BigDecimal sumMatchedQty = new Query(Env.getCtx(), MMatchPR.Table_Name, "C_OrderLine_ID=?", null)
				.setParameters(orderLine.getC_OrderLine_ID())
				.sum(MMatchPR.COLUMNNAME_QtyOrdered);

		MRequisitionLine reqLine = new MRequisitionLine(orderLine.getCtx(), orderLine.get_ValueAsInt("M_RequisitionLine_ID"), orderLine.get_TrxName());
		
		BigDecimal finalQtyOrdered = orderLine.getQtyOrdered().add(sumMatchedQty);
		
		if (finalQtyOrdered.compareTo(reqLine.getQty()) > 0)
			return "Line " +orderLine.getLine() + ", QtyOrdered = "+orderLine.getQtyOrdered() + ", Previously Matched Qty = " + sumMatchedQty 
					+ ", Total Requisition Qty = " + reqLine.getQty() + " - Total Qty Ordered > Qty on Requisition";
		return null;
	}

	private static String updateMatchPR(MOrderLine orderLine) {

		int reqLineID = orderLine.get_ValueAsInt("M_RequisitionLine_ID");
		if (reqLineID == 0)
			return "";
		
		//Update MatchPR according 
		MMatchPR matchPR = new Query(Env.getCtx(), MMatchPR.Table_Name, "C_OrderLine_ID=?", null)
				.setParameters(orderLine.getC_OrderLine_ID())
				.first();
		BigDecimal diffQty = matchPR.getQtyOrdered().subtract(orderLine.getQtyDelivered());
		matchPR.setQtyOrdered(orderLine.getQtyDelivered());
		matchPR.saveEx();
		
		MRequisitionLine reqLine = new MRequisitionLine(orderLine.getCtx(), reqLineID, orderLine.get_TrxName());
		BigDecimal QtyOrdered = reqLine.get_Value("QtyOrdered") != null ? (BigDecimal)reqLine.get_Value("QtyOrdered") : BigDecimal.ZERO;			
		BigDecimal newQtyOrdered = QtyOrdered.add(orderLine.getQtyOrdered());
					
		reqLine.set_CustomColumn("QtyOrdered", newQtyOrdered);
		reqLine.saveEx();
		

		
		MRequisitionLine rLine = new MRequisitionLine(Env.getCtx(), matchPR.getM_RequisitionLine_ID(), null);
		BigDecimal reqLineQtyOrdered = (BigDecimal) rLine.get_Value("QtyOrdered");
		rLine.set_CustomColumn("QtyOrdered", reqLineQtyOrdered.subtract(diffQty));
		rLine.saveEx();
		
		
		
		return null;
	}

	private static String removeMatchQuotation(MOrderLine orderLine) {
		StringBuilder sql = new StringBuilder("DELETE FROM ").append(X_M_MatchQuotation.Table_Name)
				.append(" WHERE C_OrderLine_ID=?");
		DB.executeUpdateEx(sql.toString(), new Object[] {orderLine.get_ID()}, orderLine.get_TrxName());

		return "";
	}

	private static String removeMatchPR(MOrderLine orderLine) {
		StringBuilder sql = new StringBuilder("DELETE FROM ").append(X_M_MatchPR.Table_Name)
				.append(" WHERE C_OrderLine_ID=?");
		DB.executeUpdateEx(sql.toString(), new Object[] {orderLine.get_ID()}, orderLine.get_TrxName());

		return "";
	}


	private static String createMatchPR(MOrderLine orderLine) {

		int reqLineID = orderLine.get_ValueAsInt("M_RequisitionLine_ID");
		if (reqLineID == 0)
			return "";
		
		MRequisitionLine reqLine = new MRequisitionLine(orderLine.getCtx(), reqLineID, orderLine.get_TrxName());
		BigDecimal QtyOrdered = reqLine.get_Value("QtyOrdered") != null ? (BigDecimal)reqLine.get_Value("QtyOrdered") : BigDecimal.ZERO;			
		BigDecimal newQtyOrdered = QtyOrdered.add(orderLine.getQtyOrdered());
				
		X_M_MatchPR match = new X_M_MatchPR(Env.getCtx(), 0, orderLine.get_TrxName());
		match.setAD_Org_ID(reqLine.getAD_Org_ID());
		match.setDateTrx(orderLine.getDateOrdered());
		match.setC_Order_ID(orderLine.getC_Order_ID());
		match.setC_OrderLine_ID(orderLine.getC_OrderLine_ID());
		match.setM_Requisition_ID(reqLine.getM_Requisition_ID());
		match.setM_RequisitionLine_ID(reqLine.getM_RequisitionLine_ID());
		match.setQtyOrdered(orderLine.getQtyOrdered());
		match.saveEx();
		
		reqLine.set_CustomColumn("QtyOrdered", newQtyOrdered);
		reqLine.saveEx();
		
		return "";

	}
}
