package id.tcs.model;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.exceptions.FillMandatoryException;
import org.compiere.model.MDocType;
import org.compiere.model.MPeriod;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.process.DocAction;
import org.compiere.process.DocumentEngine;
import org.compiere.util.Env;

public class TCS_MAdvRequest extends X_TCS_AdvRequest implements DocAction{
	
	/**	Process Message 			*/
	private String		m_processMsg = null;
	/**	Just Prepared Flag			*/
	private boolean		m_justPrepared = false;
	
	public TCS_MAdvRequest (Properties ctx, int TCS_AdvRequest_ID, String trxName)
	{
		   super (ctx, TCS_AdvRequest_ID, trxName);
	}
	   
	public TCS_MAdvRequest (Properties ctx, ResultSet rs, String trxName)
	{
		super (ctx, rs, trxName);
	}

	@Override
	public boolean processIt(String action) throws Exception {
		m_processMsg = null;
		DocumentEngine engine = new DocumentEngine (this, getDocStatus());
		return engine.processIt (action, getDocAction());//return false;
	}

	@Override
	public boolean unlockIt() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean invalidateIt() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String prepareIt() {

		if (log.isLoggable(Level.INFO)) log.info(toString());
		
		// Call model validators
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_PREPARE);
		if (m_processMsg != null)
		{
			return DocAction.STATUS_Invalid;
		}
		
		MPeriod.testPeriodOpen(getCtx(), getDateDoc(), MDocType.DOCBASETYPE_APPayment, getAD_Org_ID());
						
		// Call model validators
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_PREPARE);
		if (m_processMsg != null)
		{
			return DocAction.STATUS_Invalid;
		}

		
		//	Done
		m_justPrepared = true;
		if (!DOCACTION_Complete.equals(getDocAction()))
			setDocAction(DOCACTION_Complete);
		return DocAction.STATUS_InProgress;
	
	}

	@Override
	public boolean approveIt() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean rejectIt() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String completeIt() {
		// TODO Auto-generated method stub
		//return null;
		if (!m_justPrepared)
		{
			String status = prepareIt();
			m_justPrepared = false;
			if (!DocAction.STATUS_InProgress.equals(status))
				return status;
		}
		
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_COMPLETE);
		if (m_processMsg != null) {
			return DocAction.STATUS_Invalid;
		}
		
/*		
		//	Implicit Approval
		if (!isApproved())
			approveIt();
*/		
		if (log.isLoggable(Level.INFO)) log.info(toString());
		
//		User Validation
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_COMPLETE);
		if (m_processMsg != null) {
			return DocAction.STATUS_Invalid;
		}
			//
		
		setProcessed(true);
		setDocAction(DOCACTION_Close);
		//
		//	User Validation
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_COMPLETE);
		if (m_processMsg != null) {
			return DocAction.STATUS_Invalid;
		}
		return DocAction.STATUS_Completed;
		
		
	}

	@Override
	public boolean voidIt() {
		// TODO Auto-generated method stub
		// Before Void
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_VOID);
		if (m_processMsg != null)
			return false;		
		
		String errmsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_VOID);
		if (errmsg != null)
		{
			m_processMsg = errmsg;
			return false;
		}

		// finish
		setProcessed(true);
		setDocAction(DOCACTION_None);
		return true;
	}

	@Override
	public boolean closeIt() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean reverseCorrectIt() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean reverseAccrualIt() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean reActivateIt() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getSummary() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getDocumentInfo() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public File createPDF() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getProcessMsg() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getDoc_User_ID() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public BigDecimal getApprovalAmt() {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	protected boolean beforeSave(boolean newRecord) {
				
		Timestamp DateFrom=getDateFrom();
		Timestamp DateTo=getDateTo();
		
		if (DateTo.before(DateFrom)) {
			throw new AdempiereException("Date To tidak boleh lebih awal dari Date From");
		}
		
		setDays(getDifferenceStartFinishDate(DateTo,DateFrom));
		
		return true;
	}
	
	@Override
	protected boolean afterSave(boolean newRecord, boolean success) {
		
		return success;
	}
	
	//Taken from Habco org.toba.habco.model.MPosition
	public int getDifferenceStartFinishDate(Timestamp finishtime,
			Timestamp starttime) {
		BigDecimal diff = Env.ZERO;
		long different = finishtime.getTime() - starttime.getTime();

		int diffSeconds = (int) (long) different / 1000 % 60;
		int diffMinutestoSeconds = (int) (long) (different / (60 * 1000) % 60) * 60;
		int diffHourstoSeconds = (int) (long) (different / (60 * 60 * 1000) % 24) * 3600;
		int diffDaysSeconds = (int) (long) (different / (24 * 60 * 60 * 1000)) * 24 * 3600;
		int totalseconds = diffSeconds + diffMinutestoSeconds
				+ diffHourstoSeconds + diffDaysSeconds;
		BigDecimal hoursinseconds = new BigDecimal(3600);
		BigDecimal dayinhours = new BigDecimal(24);
		
		diff = new BigDecimal(totalseconds).divide(hoursinseconds, 4,
				RoundingMode.HALF_UP);
		diff = diff.divide(dayinhours, 0,
				RoundingMode.CEILING);
		return diff.toBigInteger().intValue();
	}
}