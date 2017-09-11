/******************************************************************************
 * Product: JPiere                                                            *
 * Copyright (C) Hideaki Hagiwara (h.hagiwara@oss-erp.co.jp)                  *
 *                                                                            *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY.                          *
 * See the GNU General Public License for more details.                       *
 *                                                                            *
 * JPiere is maintained by OSS ERP Solutions Co., Ltd.                        *
 * (http://www.oss-erp.co.jp)                                                 *
 *****************************************************************************/


package jpiere.base.plugin.org.adempiere.process;

import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.util.ProcessUtil;
import org.compiere.model.MInvoice;
import org.compiere.model.MOrder;
import org.compiere.process.ProcessInfo;
import org.compiere.process.SvrProcess;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Trx;
import org.compiere.util.Util;

import jpiere.base.plugin.org.adempiere.model.MContractContent;


/** JPIERE-0363
*
* @author Hideaki Hagiwara
*
*/
public class CallContractProcessFromDocument extends SvrProcess {
	
	MContractContent m_ContractContente = null;
	int Record_ID = 0;
	
	@Override
	protected void prepare() 
	{
        Record_ID = getRecord_ID();
		if(Record_ID > 0)
		{
			m_ContractContente = new MContractContent(getCtx(),Record_ID, get_TrxName());
		}else{
			log.log(Level.SEVERE, "Record_ID <= 0 ");
		}
	}
	
	@Override
	protected String doIt() throws Exception 
	{
		ProcessInfo pi = new ProcessInfo("Title", 0, getTable_ID(), Record_ID);
		if(Util.isEmpty(m_ContractContente.getJP_ContractProcess().getClassname()))
		{
			if(m_ContractContente.getDocBaseType().equals("SOO")
					|| m_ContractContente.getDocBaseType().equals("POO"))
			{
				pi.setClassName("jpiere.base.plugin.org.adempiere.process.DefaultCreateOrderFromContract");
				
			}else if(m_ContractContente.getDocBaseType().equals("ARI")
					|| m_ContractContente.getDocBaseType().equals("API"))
			{
				pi.setClassName("jpiere.base.plugin.org.adempiere.process.DefaultCreateBaseInvoiceFromContract");
			}
			
		}else{
			pi.setClassName(m_ContractContente.getJP_ContractProcess().getClassname());
		}
		
		pi.setAD_Client_ID(getAD_Client_ID());
		pi.setAD_User_ID(getAD_User_ID());
		pi.setAD_PInstance_ID(getAD_PInstance_ID());
		pi.setParameter(getParameter());
		boolean isOK = ProcessUtil.startJavaProcess(getCtx(), pi, Trx.get(get_TrxName(), true), false, Env.getProcessUI(getCtx()));

		if(isOK)
		{
			;
		}else{
			throw new AdempiereException(pi.getSummary());
		}
		
		String document_ID = pi.getSummary();
		int C_Document_ID = Integer.valueOf(document_ID);
		
		if(m_ContractContente.getDocBaseType().equals("SOO")
				|| m_ContractContente.getDocBaseType().equals("POO"))
		{
			MOrder order = new MOrder(getCtx(), C_Document_ID, get_TrxName());
			addBufferLog(0, null, null, Msg.getElement(getCtx(), "C_Order_ID", m_ContractContente.isSOTrx()) + " : " + order.getDocumentNo()
			, MOrder.Table_ID, C_Document_ID);//TODO メッセージ化
			
		}else if(m_ContractContente.getDocBaseType().equals("ARI")
				|| m_ContractContente.getDocBaseType().equals("API"))
		{
			MInvoice invoice = new MInvoice(getCtx(), C_Document_ID, get_TrxName());
			addBufferLog(0, null, null, Msg.getElement(getCtx(), "C_Invoice_ID", m_ContractContente.isSOTrx()) + " : " + invoice.getDocumentNo()
			, MInvoice.Table_ID, C_Document_ID);//TODO メッセージ化
		}
		
		return Msg.getMsg(getCtx(), "OK");
	}
	
}
