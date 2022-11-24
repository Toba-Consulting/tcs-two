package id.tcs.validator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.logging.Level;

import org.adempiere.base.event.IEventTopics;
import org.compiere.model.MRequisition;
import org.compiere.model.MRequisitionLine;
import org.compiere.model.MUOM;
import org.compiere.model.MUOMConversion;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.util.Env;
import org.osgi.service.event.Event;

public class TCS_RequisitionValidator {

	public static String executeEvent(Event event, PO po){
		String msg = "";
		MRequisition req = (MRequisition) po;

		if ((event.getTopic().equals(IEventTopics.DOC_BEFORE_REVERSEACCRUAL)) ||
				(event.getTopic().equals(IEventTopics.DOC_BEFORE_REVERSECORRECT))) {
			msg = checkPO(req);
		}

		else if ((event.getTopic().equals(IEventTopics.DOC_AFTER_PREPARE))) {
			//check and adjust qtyrequired for all requisition line if required
			msg += setQtyRequiredRequisitionLine(req);
		}

		return msg;
	}

	private static String setQtyRequiredRequisitionLine(MRequisition req) {

		MRequisitionLine[] lines = req.getLines();

		for (MRequisitionLine line : lines) {

			BigDecimal QtyRequired = line.get_Value("QtyRequired") != null ? (BigDecimal)line.get_Value("QtyRequired") : BigDecimal.ZERO;
			BigDecimal Qty = line.getQty();
			int C_UOM_ID = line.getC_UOM_ID();
			boolean needSave = false;
			
			BigDecimal Qty1 = Qty.setScale(MUOM.getPrecision(req.getCtx(), C_UOM_ID), RoundingMode.HALF_UP);
			if (Qty.compareTo(Qty1) != 0) {
				Qty = Qty1;
				line.setQty(Qty1);
				needSave = true;
			}

			BigDecimal QtyRequired1 = MUOMConversion.convertProductFrom (req.getCtx(), line.getM_Product_ID(),
					C_UOM_ID, Qty);
			if (QtyRequired.compareTo(QtyRequired1) != 0) {
				QtyRequired = QtyRequired1;
				line.set_CustomColumn("QtyRequired", QtyRequired);
				needSave = true;
			}
			
			if (needSave)
				line.saveEx();
		}
		
		return "";
		
	}
	
	public static String checkPO(MRequisition req){

		boolean match = false;
		String sqlWhere = "M_Requisition.M_Requisition_ID="+req.getM_Requisition_ID()+" AND co.DocStatus IN ('CO','CL') ";
		match = new Query(req.getCtx(), MRequisition.Table_Name, sqlWhere, req.get_TrxName())
				.addJoinClause("JOIN M_RequisitionLine rl on rl.M_Requisition_ID=M_Requisition.M_Requisition_ID ")
				.addJoinClause("JOIN C_OrderLine col on col.M_RequisitionLine_ID=rl.M_RequisitionLine_ID ")
				.addJoinClause("JOIN C_Order co on co.C_Order_ID=col.C_Order_ID AND co.IsSOTrx='N' ")
				.match();

		if (match) 
			return "Active Purchase Order Referencing This Requisition Exist";

		return "";
	}
}
