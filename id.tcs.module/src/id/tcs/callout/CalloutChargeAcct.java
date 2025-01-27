package id.tcs.callout;

import java.util.Properties;

import org.adempiere.base.IColumnCallout;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.MAccount;
import org.compiere.util.Env;

public class CalloutChargeAcct implements IColumnCallout{

	@Override
	public String start(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value, Object oldValue) {

		if(mField.getColumnName().equals("C_ElementValue_Charge_ID"))
			return setAccount(ctx, WindowNo, mTab, mField, value, oldValue, "C_ElementValue_Charge_ID");
		if(mField.getColumnName().equals("Ch_Expense_Acct"))
			return setElementValue(ctx, WindowNo, mTab, mField, value, oldValue);
		return null;
	}
	
	public String setAccount(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value,
			Object oldValue, String columnName) {

		if(value == null)
			return "";
	
		MAccount validAccount = MAccount.get(ctx, (int)mTab.getValue("AD_Client_ID"), 0, (int)mTab.getValue("C_AcctSchema_ID"), (int)mTab.getValue(columnName), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);		
		if (columnName.equals("C_ElementValue_Charge_ID")) {
			mTab.setValue("Ch_Expense_Acct", validAccount.get_ID());			
		}
		
		return "";
	}
	
	//Set C_ElementValue_ID
	protected String setElementValue(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value, Object oldValue) {
		
		if(value == null)
			return "";
		
		int C_ValidCombination_ID = (int) value;
		MAccount validCombination = new MAccount(Env.getCtx(), C_ValidCombination_ID, null);
		mTab.setValue("C_ElementValue_Charge_ID", validCombination.getAccount_ID());
		return "";
	}

	
}
