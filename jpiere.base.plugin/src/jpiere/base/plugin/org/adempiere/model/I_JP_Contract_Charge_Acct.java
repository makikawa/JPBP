/******************************************************************************
 * Product: iDempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2012 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY, without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program, if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package jpiere.base.plugin.org.adempiere.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import org.compiere.model.*;
import org.compiere.util.KeyNamePair;

/** Generated Interface for JP_Contract_Charge_Acct
 *  @author iDempiere (generated) 
 *  @version Release 4.1
 */
@SuppressWarnings("all")
public interface I_JP_Contract_Charge_Acct 
{

    /** TableName=JP_Contract_Charge_Acct */
    public static final String Table_Name = "JP_Contract_Charge_Acct";

    /** AD_Table_ID=1000194 */
    public static final int Table_ID = MTable.getTable_ID(Table_Name);

    KeyNamePair Model = new KeyNamePair(Table_ID, Table_Name);

    /** AccessLevel = 3 - Client - Org 
     */
    BigDecimal accessLevel = BigDecimal.valueOf(3);

    /** Load Meta Data */

    /** Column name AD_Client_ID */
    public static final String COLUMNNAME_AD_Client_ID = "AD_Client_ID";

	/** Get Client.
	  * Client/Tenant for this installation.
	  */
	public int getAD_Client_ID();

    /** Column name AD_Org_ID */
    public static final String COLUMNNAME_AD_Org_ID = "AD_Org_ID";

	/** Set Organization.
	  * Organizational entity within client
	  */
	public void setAD_Org_ID (int AD_Org_ID);

	/** Get Organization.
	  * Organizational entity within client
	  */
	public int getAD_Org_ID();

    /** Column name C_AcctSchema_ID */
    public static final String COLUMNNAME_C_AcctSchema_ID = "C_AcctSchema_ID";

	/** Set Accounting Schema.
	  * Rules for accounting
	  */
	public void setC_AcctSchema_ID (int C_AcctSchema_ID);

	/** Get Accounting Schema.
	  * Rules for accounting
	  */
	public int getC_AcctSchema_ID();

	public org.compiere.model.I_C_AcctSchema getC_AcctSchema() throws RuntimeException;

    /** Column name C_Charge_ID */
    public static final String COLUMNNAME_C_Charge_ID = "C_Charge_ID";

	/** Set Charge.
	  * Additional document charges
	  */
	public void setC_Charge_ID (int C_Charge_ID);

	/** Get Charge.
	  * Additional document charges
	  */
	public int getC_Charge_ID();

	public org.compiere.model.I_C_Charge getC_Charge() throws RuntimeException;

    /** Column name Ch_Expense_Acct */
    public static final String COLUMNNAME_Ch_Expense_Acct = "Ch_Expense_Acct";

	/** Set Charge Account.
	  * Charge Account
	  */
	public void setCh_Expense_Acct (int Ch_Expense_Acct);

	/** Get Charge Account.
	  * Charge Account
	  */
	public int getCh_Expense_Acct();

	public I_C_ValidCombination getCh_Expense_A() throws RuntimeException;

    /** Column name Created */
    public static final String COLUMNNAME_Created = "Created";

	/** Get Created.
	  * Date this record was created
	  */
	public Timestamp getCreated();

    /** Column name CreatedBy */
    public static final String COLUMNNAME_CreatedBy = "CreatedBy";

	/** Get Created By.
	  * User who created this records
	  */
	public int getCreatedBy();

    /** Column name IsActive */
    public static final String COLUMNNAME_IsActive = "IsActive";

	/** Set Active.
	  * The record is active in the system
	  */
	public void setIsActive (boolean IsActive);

	/** Get Active.
	  * The record is active in the system
	  */
	public boolean isActive();

    /** Column name JP_Ch_Expense_Acct */
    public static final String COLUMNNAME_JP_Ch_Expense_Acct = "JP_Ch_Expense_Acct";

	/** Set Charge(Recognition Doc)	  */
	public void setJP_Ch_Expense_Acct (int JP_Ch_Expense_Acct);

	/** Get Charge(Recognition Doc)	  */
	public int getJP_Ch_Expense_Acct();

	public I_C_ValidCombination getJP_Ch_Expense_A() throws RuntimeException;

    /** Column name JP_Contract_Acct_ID */
    public static final String COLUMNNAME_JP_Contract_Acct_ID = "JP_Contract_Acct_ID";

	/** Set Contract Acct Info	  */
	public void setJP_Contract_Acct_ID (int JP_Contract_Acct_ID);

	/** Get Contract Acct Info	  */
	public int getJP_Contract_Acct_ID();

	public I_JP_Contract_Acct getJP_Contract_Acct() throws RuntimeException;

    /** Column name JP_Contract_Charge_Acct_ID */
    public static final String COLUMNNAME_JP_Contract_Charge_Acct_ID = "JP_Contract_Charge_Acct_ID";

	/** Set JP_Contract_Charge_Acct	  */
	public void setJP_Contract_Charge_Acct_ID (int JP_Contract_Charge_Acct_ID);

	/** Get JP_Contract_Charge_Acct	  */
	public int getJP_Contract_Charge_Acct_ID();

    /** Column name JP_Contract_Charge_Acct_UU */
    public static final String COLUMNNAME_JP_Contract_Charge_Acct_UU = "JP_Contract_Charge_Acct_UU";

	/** Set JP_Contract_Charge_Acct_UU	  */
	public void setJP_Contract_Charge_Acct_UU (String JP_Contract_Charge_Acct_UU);

	/** Get JP_Contract_Charge_Acct_UU	  */
	public String getJP_Contract_Charge_Acct_UU();

    /** Column name Updated */
    public static final String COLUMNNAME_Updated = "Updated";

	/** Get Updated.
	  * Date this record was updated
	  */
	public Timestamp getUpdated();

    /** Column name UpdatedBy */
    public static final String COLUMNNAME_UpdatedBy = "UpdatedBy";

	/** Get Updated By.
	  * User who updated this records
	  */
	public int getUpdatedBy();
}
