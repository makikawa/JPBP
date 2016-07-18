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

import jpiere.base.plugin.org.adempiere.model.MEstimation;
import jpiere.base.plugin.org.adempiere.model.MEstimationLine;

import org.adempiere.util.Callback;
import org.adempiere.util.IProcessUI;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.PO;
import org.compiere.process.DocAction;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.Env;
import org.compiere.util.Msg;

/**
 * JPIERE-0185 : Create SO from Estimation
 *
 * @author Hideaki Hagiwara
 *
 */
public class CreateSOfromEstimation extends SvrProcess {

	private int			p_JP_Estimation_ID = 0;
	private String		p_DocAction = null;
	private MEstimation estimation = null;
	IProcessUI processUI = null;
	
	@Override
	protected void prepare() {

		p_JP_Estimation_ID = getRecord_ID();

		
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++)
		{
			String name = para[i].getParameterName();
			if (para[i].getParameter() == null){
				;
			}else if (name.equals("DocAction")){
				p_DocAction = para[i].getParameterAsString();
			}else{
				log.log(Level.SEVERE, "Unknown Parameter: " + name);
			}//if

		}//for
		
		processUI = Env.getProcessUI(getCtx());
		estimation = new MEstimation(getCtx(), p_JP_Estimation_ID, get_TrxName()) ;
		if(processUI != null && estimation.getLink_Order_ID() != 0)
		{
			//Already Sales Order created, Do you want to create Sales Order again?
			processUI.ask(Msg.getMsg(getCtx(), "JP_CreateSOfromEstimationAgain"), new Callback<Boolean>() {

				@Override
				public void onCallback(Boolean result)
				{
					if (result)
					{
						createSO();
					}else{
						;
					}
		        }

			});//FDialog.
		}
	}

	private String createSO() 
	{

		MEstimationLine[] eLines = estimation.getLines();
		
		MOrder order = new MOrder(getCtx(), 0, get_TrxName()) ;
		PO.copyValues(estimation, order);
		order.setAD_Org_ID(estimation.getAD_Org_ID());
		order.setDocumentNo(null);
		order.setC_DocTypeTarget_ID(estimation.getC_DocType_ID());
		order.setDocStatus(DocAction.STATUS_Drafted);
		order.setDocAction(DocAction.ACTION_Complete);		
		order.saveEx(get_TrxName());
		
		estimation.setLink_Order_ID(order.getC_Order_ID());
		estimation.saveEx(get_TrxName());
		
		for(int i = 0; i < eLines.length; i++)
		{
			MOrderLine oLine = new MOrderLine(order);
			PO.copyValues(eLines[i], oLine);	
			oLine.setAD_Org_ID(eLines[i].getAD_Org_ID());
			oLine.saveEx(get_TrxName());
			
		}//for
		
		order.processIt(p_DocAction);
		order.saveEx(get_TrxName());
		
		return order.getDocumentInfo();
	}
	
	@Override
	protected String doIt() throws Exception 
	{
		if(processUI == null || estimation.getLink_Order_ID() == 0)
		{
			return createSO();
		}
		
		return "";
	}

}