package id.taowi.process;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.compiere.model.I_C_AllocationLine;
import org.compiere.model.MAllocationHdr;
import org.compiere.model.MAllocationLine;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.compiere.util.Env;

import id.tcs.model.MTCSMatchAllocation;

public class NewGenerateMatchAllocation extends SvrProcess {

	@Override
	protected void prepare() {
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++) {
			String name = para[i].getParameterName();
			if (para[i].getParameter() == null) {
				;
			} else if (para[i].getParameterName().equalsIgnoreCase("C_AllocationHdr_ID")) {
				p_Alloc_ID = para[i].getParameterAsInt();

			} else {
				log.log(Level.SEVERE, "Unknown Parameter: "+name);
			}
		}
	}

	int p_Alloc_ID = 0;

	@Override
	protected String doIt() throws Exception {

		MAllocationHdr alloc = new MAllocationHdr(getCtx(), p_Alloc_ID, get_TrxName());

		//Match Payment to Invoice
		String wherePaymentAndInvoice = "C_AllocationHdr_ID=? AND C_Payment_ID > 0 AND C_Invoice_ID > 0";
		List<MAllocationLine> piLines = new Query(alloc.getCtx(), MAllocationLine.Table_Name, 
				wherePaymentAndInvoice , alloc.get_TrxName())
				.setParameters(alloc.get_ID())
				.list();

		for (MAllocationLine piLine : piLines) {
			createPaymentInvoiceMatchAlloc(piLine);

		}

		String whereChargeOnly = "C_AllocationHdr_ID=? AND C_Charge_ID > 0";
		List<MAllocationLine> coLines = new Query(alloc.getCtx(), MAllocationLine.Table_Name, 
				whereChargeOnly , alloc.get_TrxName())
				.setParameters(alloc.get_ID())
				.list();

		ArrayList<BigDecimal> chargeAmountList = new ArrayList<BigDecimal>(coLines.size());

		for (MAllocationLine coLine : coLines) {
			chargeAmountList.add(coLine.getAmount().abs());
		}

		/* Charge is not mandatory
		if (coLines.isEmpty())
			return "No Lines";
		 */

		//Match remaining payment to payment
		String wherePaymentOnly = "C_AllocationHdr_ID=? AND C_Payment_ID > 0 AND C_Invoice_ID = 0";
		List<MAllocationLine> poLines = new Query(alloc.getCtx(), I_C_AllocationLine.Table_Name, 
				wherePaymentOnly , alloc.get_TrxName())
				.setParameters(alloc.get_ID())
				.setOrderBy(I_C_AllocationLine.COLUMNNAME_Amount)
				.list();

		String wherePaymentNegOnly = "C_AllocationHdr_ID=? AND C_Payment_ID > 0 AND C_Invoice_ID = 0 AND Amount < 0";
		List<MAllocationLine> ioNegLines = new Query(alloc.getCtx(), MAllocationLine.Table_Name, 
				wherePaymentNegOnly , alloc.get_TrxName())
				.setParameters(alloc.get_ID())
				.setOrderBy(I_C_AllocationLine.COLUMNNAME_Amount + " ASC")
				.list();

		ArrayList<BigDecimal> negPayAmountList = new ArrayList<BigDecimal>(ioNegLines.size());

		for (MAllocationLine line : ioNegLines) {
			negPayAmountList.add(line.getAmount());
		}

		String wherePayPosOnly = "C_AllocationHdr_ID=? AND C_Payment_ID > 0 AND C_Invoice_ID = 0 AND Amount > 0";
		List<MAllocationLine> ioPosLines = new Query(alloc.getCtx(), MAllocationLine.Table_Name, 
				wherePayPosOnly , alloc.get_TrxName())
				.setParameters(alloc.get_ID())
				.setOrderBy(I_C_AllocationLine.COLUMNNAME_Amount + " DESC")
				.list();

		ArrayList<BigDecimal> posPayAmountList = new ArrayList<BigDecimal>(ioPosLines.size());

		for (MAllocationLine line : ioPosLines) {
			posPayAmountList.add(line.getAmount());
		}

		for (int i = 0; i < ioNegLines.size(); i++) {
			BigDecimal negPayAmt = (BigDecimal) negPayAmountList.get(i);
			BigDecimal AppliedAmt = negPayAmt.abs();
			for (int j = 0; j < ioPosLines.size() && AppliedAmt.signum() != 0; j++)
			{
				BigDecimal posPayAmt = (BigDecimal)posPayAmountList.get(j);
				if (posPayAmt.signum() == 0)
					continue;

				if (posPayAmt.signum() != AppliedAmt.signum())	// only match different sign (otherwise appliedAmt increases)
				{											
					//if (log.isLoggable(Level.CONFIG)) log.config(".. with payment #" + j + ", Amt=" + posInvAmt);

					BigDecimal amount = AppliedAmt;
					if (amount.abs().compareTo(posPayAmt.abs()) > 0)  // if there's more open on the invoice
						amount = posPayAmt;							// than left in the payment

					createInvToInvMatchAlloc(ioNegLines.get(i), ioPosLines.get(j), ioNegLines.get(i).getParent(), amount);

					//  subtract amount from Payment/Invoice
					AppliedAmt = AppliedAmt.subtract(amount);
					posPayAmt = posPayAmt.subtract(amount);
					negPayAmt = negPayAmt.add(amount);
					if (log.isLoggable(Level.FINE)) log.fine("Allocation Amount=" + amount + " - Remaining  Applied=" + AppliedAmt + ", Payment=" + posPayAmt);
					posPayAmountList.set(j, posPayAmt);  //  update
					negPayAmountList.set(i, negPayAmt);
				}	//	for all applied amounts
				if (negPayAmt.signum() == 0)
					continue;
			}
		}

		//Match remaining invoice to invoice
		String whereInvoiceNegOnly = "C_AllocationHdr_ID=? AND C_Payment_ID = 0 AND C_Invoice_ID > 0 AND Amount < 0";
		List<MAllocationLine> ioNegInvLines = new Query(alloc.getCtx(), MAllocationLine.Table_Name, 
				whereInvoiceNegOnly , alloc.get_TrxName())
				.setParameters(alloc.get_ID())
				.setOrderBy(I_C_AllocationLine.COLUMNNAME_Amount + " ASC")
				.list();

		ArrayList<BigDecimal> negInvAmountList = new ArrayList<BigDecimal>(ioNegInvLines.size());

		for (MAllocationLine line : ioNegInvLines) {
			negInvAmountList.add(line.getAmount());
		}
		String whereInvoicePosOnly = "C_AllocationHdr_ID=? AND C_Payment_ID = 0 AND C_Invoice_ID > 0 AND Amount > 0";
		List<MAllocationLine> ioPosInvLines = new Query(alloc.getCtx(), MAllocationLine.Table_Name, 
				whereInvoicePosOnly , alloc.get_TrxName())
				.setParameters(alloc.get_ID())
				.setOrderBy(I_C_AllocationLine.COLUMNNAME_Amount + " DESC")
				.list();

		ArrayList<BigDecimal> posInvAmountList = new ArrayList<BigDecimal>(ioPosInvLines.size());

		for (MAllocationLine line : ioPosInvLines) {
			posInvAmountList.add(line.getAmount());
		}

		for (int i = 0; i < ioNegInvLines.size(); i++) {
			BigDecimal negInvAmt = (BigDecimal) negInvAmountList.get(i);
			BigDecimal AppliedAmt = negInvAmt.abs();
			for (int j = 0; j < ioPosInvLines.size() && AppliedAmt.signum() != 0; j++)
			{
				BigDecimal posInvAmt = (BigDecimal)posInvAmountList.get(j);
				if (posInvAmt.signum() == 0)
					continue;

				if (posInvAmt.signum() != AppliedAmt.signum())	// only match different sign (otherwise appliedAmt increases)
				{											
					//if (log.isLoggable(Level.CONFIG)) log.config(".. with payment #" + j + ", Amt=" + posInvAmt);

					BigDecimal amount = AppliedAmt;
					if (amount.abs().compareTo(posInvAmt.abs()) > 0)  // if there's more open on the invoice
						amount = posInvAmt;							// than left in the payment

					createInvToInvMatchAlloc(ioNegInvLines.get(i), ioPosInvLines.get(j), ioNegInvLines.get(i).getParent(), amount);

					//  subtract amount from Payment/Invoice
					AppliedAmt = AppliedAmt.subtract(amount);
					posInvAmt = posInvAmt.subtract(amount);
					negInvAmt = negInvAmt.add(amount);
					if (log.isLoggable(Level.FINE)) log.fine("Allocation Amount=" + amount + " - Remaining  Applied=" + AppliedAmt + ", Payment=" + posInvAmt);
					posInvAmountList.set(j, posInvAmt);  //  update
					negInvAmountList.set(i, negInvAmt);
				}	//	for all applied amounts
				if (negInvAmt.signum() == 0)
					continue;
			}	//	loop through payments for invoice
			/*
			BigDecimal negAmount = negLine.getAmount();

			for (MAllocationLine posLine : ioPosLines) {
				if (currentPosLine == null) {
					currentPosLine = posLine;
					unmatchedPosAmt = posLine.getAmount();
				} else if (currentPosLine!=posLine){
					continue;
				}
				BigDecimal matchAmt = Env.ZERO;
				if (negAmount.add(unmatchedPosAmt).compareTo(Env.ZERO) < 0) {
					matchAmt = unmatchedPosAmt;
					createInvToInvMatchAlloc(negLine, posLine, negLine.getParent(), matchAmt);
					negAmount = negAmount.add(unmatchedPosAmt);
				} else {
					matchAmt = negAmount.negate();
					createInvToInvMatchAlloc(negLine, posLine, negLine.getParent(), matchAmt);
					currentPosLine = posLine;
					unmatchedPosAmt = unmatchedPosAmt.add(negAmount);
					break;
				}
			}
			 */
		}

		//Match remaining payment to charge
		for (int i = 0; i < ioNegLines.size(); i++) {
			BigDecimal negPayAmt = (BigDecimal) negPayAmountList.get(i);
			if (negPayAmt.signum() == 0)
				continue;

			BigDecimal AppliedAmt = negPayAmt.abs();
			for (int j = 0; j < coLines.size() && AppliedAmt.signum() != 0; j++)
			{
				BigDecimal chargeAmt = (BigDecimal)chargeAmountList.get(j);
				if (chargeAmt.signum() == 0)
					continue;

				//if (log.isLoggable(Level.CONFIG)) log.config(".. with payment #" + j + ", Amt=" + posInvAmt);

				BigDecimal amount = AppliedAmt;
				if (amount.abs().compareTo(chargeAmt.abs()) > 0)  // if there's more open on the invoice
					amount = chargeAmt;							// than left in the payment

				createPayToChargeMatchAlloc(ioNegLines.get(i), coLines.get(j).getC_Charge_ID(), ioNegLines.get(i).getParent(), amount.abs());

				//  subtract amount from Payment/Invoice
				AppliedAmt = AppliedAmt.subtract(amount);
				chargeAmt = chargeAmt.subtract(amount);
				negPayAmt = negPayAmt.add(amount);
				//if (log.isLoggable(Level.FINE)) log.fine("Allocation Amount=" + amount + " - Remaining  Applied=" + AppliedAmt + ", Payment=" + chargeAmt);
				chargeAmountList.set(j, chargeAmt);  //  update
				negPayAmountList.set(i, negPayAmt);

				if (negPayAmt.signum() == 0)
					continue;
			}
		}
		
		for (int i = 0; i < ioPosLines.size(); i++) {
			BigDecimal posPayAmt = (BigDecimal) posPayAmountList.get(i);
			if (posPayAmt.signum() == 0)
				continue;

			BigDecimal AppliedAmt = posPayAmt.abs();
			for (int j = 0; j < coLines.size() && AppliedAmt.signum() != 0; j++)
			{
				BigDecimal chargeAmt = (BigDecimal)chargeAmountList.get(j);
				if (chargeAmt.signum() == 0)
					continue;

				//if (log.isLoggable(Level.CONFIG)) log.config(".. with payment #" + j + ", Amt=" + posInvAmt);

				BigDecimal amount = AppliedAmt;
				if (amount.abs().compareTo(chargeAmt.abs()) > 0)  // if there's more open on the invoice
					amount = chargeAmt;							// than left in the payment

				createPayToChargeMatchAlloc(ioPosLines.get(i), coLines.get(j).getC_Charge_ID(), ioPosLines.get(i).getParent(), amount.abs());

				//  subtract amount from Payment/Invoice
				AppliedAmt = AppliedAmt.subtract(amount);
				chargeAmt = chargeAmt.subtract(amount);
				posPayAmt = posPayAmt.add(amount);
				//if (log.isLoggable(Level.FINE)) log.fine("Allocation Amount=" + amount + " - Remaining  Applied=" + AppliedAmt + ", Payment=" + chargeAmt);
				chargeAmountList.set(j, chargeAmt);  //  update
				posPayAmountList.set(i, posPayAmt);

				if (posPayAmt.signum() == 0)
					continue;
			}
		}

		//Match remaining invoice to charge
		for (int i = 0; i < ioNegInvLines.size(); i++) {
			BigDecimal negInvAmt = (BigDecimal) negInvAmountList.get(i);
			if (negInvAmt.signum() == 0)
				continue;

			BigDecimal AppliedAmt = negInvAmt.abs();
			for (int j = 0; j < coLines.size() && AppliedAmt.signum() != 0; j++)
			{
				BigDecimal chargeAmt = (BigDecimal)chargeAmountList.get(j);
				if (chargeAmt.signum() == 0)
					continue;

				//if (log.isLoggable(Level.CONFIG)) log.config(".. with payment #" + j + ", Amt=" + posInvAmt);

				BigDecimal amount = AppliedAmt;
				if (amount.abs().compareTo(chargeAmt.abs()) > 0)  // if there's more open on the invoice
					amount = chargeAmt;							// than left in the payment

				createInvToChargeMatchAlloc(ioNegInvLines.get(i), coLines.get(j).getC_Charge_ID(), ioNegInvLines.get(i).getParent(), amount.abs());

				//  subtract amount from Payment/Invoice
				AppliedAmt = AppliedAmt.subtract(amount);
				chargeAmt = chargeAmt.subtract(amount);
				negInvAmt = negInvAmt.add(amount);
				//if (log.isLoggable(Level.FINE)) log.fine("Allocation Amount=" + amount + " - Remaining  Applied=" + AppliedAmt + ", Payment=" + chargeAmt);
				chargeAmountList.set(j, chargeAmt);  //  update
				negInvAmountList.set(i, negInvAmt);

				if (negInvAmt.signum() == 0)
					continue;
			}
		}

		for (int i = 0; i < ioPosInvLines.size(); i++) {
			BigDecimal posInvAmt = (BigDecimal) posInvAmountList.get(i);
			if (posInvAmt.signum() == 0)
				continue;

			BigDecimal AppliedAmt = posInvAmt.abs();
			for (int j = 0; j < coLines.size() && AppliedAmt.signum() != 0; j++)
			{
				BigDecimal chargeAmt = (BigDecimal)chargeAmountList.get(j);
				if (chargeAmt.signum() == 0)
					continue;

				//if (log.isLoggable(Level.CONFIG)) log.config(".. with payment #" + j + ", Amt=" + posInvAmt);

				BigDecimal amount = AppliedAmt;
				if (amount.abs().compareTo(chargeAmt.abs()) > 0)  // if there's more open on the invoice
					amount = chargeAmt;							// than left in the payment

				createInvToChargeMatchAlloc(ioPosInvLines.get(i), coLines.get(j).getC_Charge_ID(), ioPosInvLines.get(i).getParent(), amount.abs());

				//  subtract amount from Payment/Invoice
				AppliedAmt = AppliedAmt.subtract(amount);
				chargeAmt = chargeAmt.subtract(amount);
				posInvAmt = posInvAmt.add(amount);
				//if (log.isLoggable(Level.FINE)) log.fine("Allocation Amount=" + amount + " - Remaining  Applied=" + AppliedAmt + ", Payment=" + chargeAmt);
				chargeAmountList.set(j, chargeAmt);  //  update
				posInvAmountList.set(i, posInvAmt);

				if (posInvAmt.signum() == 0)
					continue;
			}
		}

		return "Success";
	}


	private static void createPayToChargeMatchAlloc(MAllocationLine payLine, int C_Charge_ID, MAllocationHdr allocHdr, BigDecimal matchAmt) {
		//first match allocation record - neg invoice
		MTCSMatchAllocation matchAllocNegInvoice = new MTCSMatchAllocation(allocHdr.getCtx(), 0, allocHdr.get_TrxName());
		matchAllocNegInvoice.setAD_Org_ID(allocHdr.getAD_Org_ID());
		matchAllocNegInvoice.setC_Payment_ID(payLine.getC_Payment_ID());
		matchAllocNegInvoice.setC_Invoice_ID(0);
		matchAllocNegInvoice.setMatch_Payment_ID(0);
		matchAllocNegInvoice.setMatch_Invoice_ID(0);
		matchAllocNegInvoice.setC_Charge_ID(C_Charge_ID);
		matchAllocNegInvoice.setAllocatedAmt(matchAmt);
		matchAllocNegInvoice.setC_AllocationHdr_ID(allocHdr.getC_AllocationHdr_ID());
		matchAllocNegInvoice.setC_BPartner_ID(payLine.getC_BPartner_ID());
		matchAllocNegInvoice.setC_Currency_ID(allocHdr.getC_Currency_ID());
		matchAllocNegInvoice.setDescription(allocHdr.getDescription());
		matchAllocNegInvoice.setDiscountAmt(Env.ZERO);
		matchAllocNegInvoice.setWriteOffAmt(Env.ZERO);
		matchAllocNegInvoice.setOverUnderAmt(Env.ZERO);
		matchAllocNegInvoice.saveEx();
	}
	
	private static void createInvToChargeMatchAlloc(MAllocationLine invLine, int C_Charge_ID, MAllocationHdr allocHdr, BigDecimal matchAmt) {
		//first match allocation record - neg invoice
		MTCSMatchAllocation matchAllocNegInvoice = new MTCSMatchAllocation(allocHdr.getCtx(), 0, allocHdr.get_TrxName());
		matchAllocNegInvoice.setAD_Org_ID(allocHdr.getAD_Org_ID());
		matchAllocNegInvoice.setC_Payment_ID(0);
		matchAllocNegInvoice.setC_Invoice_ID(invLine.getC_Invoice_ID());
		matchAllocNegInvoice.setMatch_Payment_ID(0);
		matchAllocNegInvoice.setMatch_Invoice_ID(0);
		matchAllocNegInvoice.setC_Charge_ID(C_Charge_ID);
		matchAllocNegInvoice.setAllocatedAmt(matchAmt);
		matchAllocNegInvoice.setC_AllocationHdr_ID(allocHdr.getC_AllocationHdr_ID());
		matchAllocNegInvoice.setC_BPartner_ID(invLine.getC_BPartner_ID());
		matchAllocNegInvoice.setC_Currency_ID(allocHdr.getC_Currency_ID());
		matchAllocNegInvoice.setDescription(allocHdr.getDescription());
		matchAllocNegInvoice.setDiscountAmt(Env.ZERO);
		matchAllocNegInvoice.setWriteOffAmt(Env.ZERO);
		matchAllocNegInvoice.setOverUnderAmt(Env.ZERO);
		matchAllocNegInvoice.saveEx();
	}
	
	private static void createInvToInvMatchAlloc(MAllocationLine negLine, MAllocationLine posLine, MAllocationHdr allocHdr, BigDecimal matchAmt) {
		//first match allocation record - neg invoice
		MTCSMatchAllocation matchAllocNegInvoice = new MTCSMatchAllocation(allocHdr.getCtx(), 0, allocHdr.get_TrxName());
		matchAllocNegInvoice.setAD_Org_ID(allocHdr.getAD_Org_ID());
		matchAllocNegInvoice.setC_Payment_ID(0);
		matchAllocNegInvoice.setC_Invoice_ID(negLine.getC_Invoice_ID());
		matchAllocNegInvoice.setMatch_Payment_ID(0);
		matchAllocNegInvoice.setMatch_Invoice_ID(posLine.getC_Invoice_ID());
		matchAllocNegInvoice.setC_Charge_ID(0);
		matchAllocNegInvoice.setAllocatedAmt(matchAmt);
		matchAllocNegInvoice.setC_AllocationHdr_ID(allocHdr.getC_AllocationHdr_ID());
		matchAllocNegInvoice.setC_BPartner_ID(negLine.getC_BPartner_ID());
		matchAllocNegInvoice.setC_Currency_ID(allocHdr.getC_Currency_ID());
		matchAllocNegInvoice.setDescription(allocHdr.getDescription());
		matchAllocNegInvoice.setDiscountAmt(Env.ZERO);
		matchAllocNegInvoice.setWriteOffAmt(Env.ZERO);
		matchAllocNegInvoice.setOverUnderAmt(Env.ZERO);
		matchAllocNegInvoice.saveEx();

		//second match allocation record - pos invoice
		MTCSMatchAllocation matchAllocPosInvoice = new MTCSMatchAllocation(allocHdr.getCtx(), 0, allocHdr.get_TrxName());
		matchAllocPosInvoice.setAD_Org_ID(allocHdr.getAD_Org_ID());
		matchAllocPosInvoice.setC_Payment_ID(0);
		matchAllocPosInvoice.setC_Invoice_ID(posLine.getC_Invoice_ID());
		matchAllocPosInvoice.setMatch_Payment_ID(0);
		matchAllocPosInvoice.setMatch_Invoice_ID(negLine.getC_Invoice_ID());
		matchAllocPosInvoice.setC_Charge_ID(0);
		matchAllocPosInvoice.setAllocatedAmt(matchAmt);
		matchAllocPosInvoice.setC_AllocationHdr_ID(allocHdr.getC_AllocationHdr_ID());
		matchAllocPosInvoice.setC_BPartner_ID(posLine.getC_BPartner_ID());
		matchAllocPosInvoice.setC_Currency_ID(allocHdr.getC_Currency_ID());
		matchAllocPosInvoice.setDescription(allocHdr.getDescription());
		matchAllocPosInvoice.setDiscountAmt(Env.ZERO);
		matchAllocPosInvoice.setWriteOffAmt(Env.ZERO);
		matchAllocPosInvoice.setOverUnderAmt(Env.ZERO);
		matchAllocPosInvoice.saveEx();
		//@win
	}

	private static void createPayToPayMatchAlloc(MAllocationLine negLine, MAllocationLine posLine, MAllocationHdr allocHdr, BigDecimal matchAmt) {
		//first match allocation record - neg payment
		MTCSMatchAllocation matchAllocNegPayment = new MTCSMatchAllocation(allocHdr.getCtx(), 0, allocHdr.get_TrxName());
		matchAllocNegPayment.setAD_Org_ID(allocHdr.getAD_Org_ID());
		matchAllocNegPayment.setC_Payment_ID(negLine.getC_Payment_ID());
		matchAllocNegPayment.setC_Invoice_ID(0);
		matchAllocNegPayment.setMatch_Payment_ID(posLine.getC_Payment_ID());
		matchAllocNegPayment.setMatch_Invoice_ID(0);
		matchAllocNegPayment.setC_Charge_ID(0);
		matchAllocNegPayment.setAllocatedAmt(matchAmt);
		matchAllocNegPayment.setC_AllocationHdr_ID(allocHdr.getC_AllocationHdr_ID());
		matchAllocNegPayment.setC_BPartner_ID(negLine.getC_BPartner_ID());
		matchAllocNegPayment.setC_Currency_ID(allocHdr.getC_Currency_ID());
		matchAllocNegPayment.setDescription(allocHdr.getDescription());
		matchAllocNegPayment.setDiscountAmt(Env.ZERO);
		matchAllocNegPayment.setWriteOffAmt(Env.ZERO);
		matchAllocNegPayment.setOverUnderAmt(Env.ZERO);
		matchAllocNegPayment.saveEx();

		//second match allocation record - pos payment
		MTCSMatchAllocation matchAllocPosPayment = new MTCSMatchAllocation(allocHdr.getCtx(), 0, allocHdr.get_TrxName());
		matchAllocPosPayment.setAD_Org_ID(allocHdr.getAD_Org_ID());
		matchAllocPosPayment.setC_Payment_ID(posLine.getC_Payment_ID());
		matchAllocPosPayment.setC_Invoice_ID(0);
		matchAllocPosPayment.setMatch_Payment_ID(negLine.getC_Payment_ID());
		matchAllocPosPayment.setMatch_Invoice_ID(0);
		matchAllocPosPayment.setC_Charge_ID(0);
		matchAllocPosPayment.setAllocatedAmt(matchAmt);
		matchAllocPosPayment.setC_AllocationHdr_ID(allocHdr.getC_AllocationHdr_ID());
		matchAllocPosPayment.setC_BPartner_ID(posLine.getC_BPartner_ID());
		matchAllocPosPayment.setC_Currency_ID(allocHdr.getC_Currency_ID());
		matchAllocPosPayment.setDescription(allocHdr.getDescription());
		matchAllocPosPayment.setDiscountAmt(Env.ZERO);
		matchAllocPosPayment.setWriteOffAmt(Env.ZERO);
		matchAllocPosPayment.setOverUnderAmt(Env.ZERO);
		matchAllocPosPayment.saveEx();
		//@win
	}


	private static void createPaymentInvoiceMatchAlloc(MAllocationLine line) {
		//first match allocation record - payment
		MTCSMatchAllocation matchAllocatePayment = new MTCSMatchAllocation(line.getCtx(), 0, line.get_TrxName());
		matchAllocatePayment.setAD_Org_ID(line.getAD_Org_ID());
		matchAllocatePayment.setC_Payment_ID(line.getC_Payment_ID());
		matchAllocatePayment.setC_Invoice_ID(0);
		matchAllocatePayment.setMatch_Payment_ID(0);
		matchAllocatePayment.setMatch_Invoice_ID(line.getC_Invoice_ID());
		matchAllocatePayment.setC_Charge_ID(0);
		matchAllocatePayment.setAllocatedAmt(line.getAmount());
		matchAllocatePayment.setC_AllocationHdr_ID(line.getC_AllocationHdr_ID());
		matchAllocatePayment.setC_BPartner_ID(line.getC_BPartner_ID());
		matchAllocatePayment.setC_Currency_ID(line.getParent().getC_Currency_ID());
		matchAllocatePayment.setDescription(line.getParent().getDescription());
		matchAllocatePayment.setDiscountAmt(line.getDiscountAmt());
		matchAllocatePayment.setWriteOffAmt(line.getWriteOffAmt());
		matchAllocatePayment.setOverUnderAmt(line.getOverUnderAmt());
		matchAllocatePayment.saveEx(line.get_TrxName());

		//first match allocation record - invoice
		MTCSMatchAllocation matchAllocationInvoice = new MTCSMatchAllocation(line.getCtx(), 0, line.get_TrxName());
		matchAllocationInvoice.setAD_Org_ID(line.getAD_Org_ID());
		matchAllocationInvoice.setC_Payment_ID(0);
		matchAllocationInvoice.setC_Invoice_ID(line.getC_Invoice_ID());
		matchAllocationInvoice.setMatch_Payment_ID(line.getC_Payment_ID());
		matchAllocationInvoice.setMatch_Invoice_ID(0);
		matchAllocationInvoice.setC_Charge_ID(0);
		matchAllocationInvoice.setAllocatedAmt(line.getAmount());
		matchAllocationInvoice.setC_AllocationHdr_ID(line.getC_AllocationHdr_ID());
		matchAllocationInvoice.setC_BPartner_ID(line.getC_BPartner_ID());
		matchAllocationInvoice.setC_Currency_ID(line.getParent().getC_Currency_ID());
		matchAllocationInvoice.setDescription(line.getParent().getDescription());
		matchAllocationInvoice.setDiscountAmt(line.getDiscountAmt());
		matchAllocationInvoice.setWriteOffAmt(line.getWriteOffAmt());
		matchAllocationInvoice.setOverUnderAmt(line.getOverUnderAmt());
		matchAllocationInvoice.saveEx();
		//@win
	}
}
