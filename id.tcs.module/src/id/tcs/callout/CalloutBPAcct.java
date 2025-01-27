package id.tcs.callout;

import java.util.Properties;

import org.adempiere.base.IColumnCallout;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.MAccount;

public class CalloutBPAcct implements IColumnCallout{

	@Override
	public String start(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value, Object oldValue) {

		if (mField.getColumnName().equals("C_ElementValue_CP_ID"))
			return setAccount(ctx, WindowNo, mTab, mField, value, oldValue, "C_ElementValue_CP_ID");
		if (mField.getColumnName().equals("C_ElementValue_CR_ID"))
			return setAccount(ctx, WindowNo, mTab, mField, value, oldValue, "C_ElementValue_CR_ID");
		if (mField.getColumnName().equals("C_ElementValue_VL_ID"))
			return setAccount(ctx, WindowNo, mTab, mField, value, oldValue, "C_ElementValue_VL_ID");
		if (mField.getColumnName().equals("C_ElementValue_VP_ID"))
			return setAccount(ctx, WindowNo, mTab, mField, value, oldValue, "C_ElementValue_VP_ID");
		if (mField.getColumnName().equals("C_Receivable_Acct")
				|| mField.getColumnName().equals("C_Prepayment_Acct")
				|| mField.getColumnName().equals("V_Liability_Acct")
				|| mField.getColumnName().equals("V_Prepayment_Acct")
				)
			return setElementValue(ctx, WindowNo, mTab, mField, value, oldValue);
		return null;
	}

	public String setAccount(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value,
			Object oldValue, String columnName) {

		if (value == null) 
			return "";
		
		MAccount validAccount = MAccount.get(ctx, (int)mTab.getValue("AD_Client_ID"), 0, (int)mTab.getValue("C_AcctSchema_ID"), (int)mTab.getValue(columnName), 0, 0, (int)mTab.getValue("C_BPartner_ID"), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);
		if(columnName.equals("C_ElementValue_CR_ID"))
			mTab.setValue("C_Receivable_Acct", validAccount.get_ID());
		else if(columnName.equals("C_ElementValue_CP_ID"))
			mTab.setValue("C_Prepayment_Acct", validAccount.get_ID());
		else if(columnName.equals("C_ElementValue_VL_ID"))
			mTab.setValue("V_Liability_Acct", validAccount.get_ID());
		else if(columnName.equals("C_ElementValue_VP_ID"))
			mTab.setValue("V_Prepayment_Acct", validAccount.get_ID());
		
		return "";
	}
		
	//Set Element Value
	protected String setElementValue(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value, Object oldValue) {
		if (value == null)
			return "";
		
		int C_ValidCombination_ID = (int) value;
		MAccount validCombination = new MAccount(ctx, C_ValidCombination_ID, null);
		
		if (mField.getColumnName().equals("C_Prepayment_Acct")) {
			mTab.setValue("C_ElementValue_CP_ID", validCombination.getAccount_ID());
		}else if (mField.getColumnName().equals("C_Receivable_Acct")) {
			mTab.setValue("C_ElementValue_CR_ID", validCombination.getAccount_ID());
		}else if (mField.getColumnName().equals("V_Liability_Acct")) {
			mTab.setValue("C_ElementValue_VL_ID", validCombination.getAccount_ID());
		}else if (mField.getColumnName().equals("V_Prepayment_Acct")) {
			mTab.setValue("C_ElementValue_VP_ID", validCombination.getAccount_ID());
		}
		
		return "";
	}
	
}
