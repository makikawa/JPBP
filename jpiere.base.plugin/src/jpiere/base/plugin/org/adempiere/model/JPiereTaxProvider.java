/******************************************************************************
 * Product: JPiere(ジェイピエール) - JPiere Base Plugin                       *
 * Copyright (C) Hideaki Hagiwara All Rights Reserved.                        *
 * このプログラムはGNU Gneral Public Licens Version2のもと公開しています。    *
 * このプログラムは自由に活用してもらう事を期待して公開していますが、         *
 * いかなる保証もしていません。                                               *
 * 著作権は萩原秀明(h.hagiwara@oss-erp.co.jp)が保持し、サポートサービスは     *
 * 株式会社オープンソース・イーアールピー・ソリューションズで                 *
 * 提供しています。サポートをご希望の際には、                                 *
 * 株式会社オープンソース・イーアールピー・ソリューションズまでご連絡下さい。 *
 * http://www.oss-erp.co.jp/                                                  *
 *****************************************************************************/
package jpiere.base.plugin.org.adempiere.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;

import org.adempiere.exceptions.DBException;
import org.adempiere.model.ITaxProvider;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.I_C_TaxProvider;
import org.compiere.model.MBPartner;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MInvoiceTax;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MOrderTax;
import org.compiere.model.MRMA;
import org.compiere.model.MRMALine;
import org.compiere.model.MRMATax;
import org.compiere.model.MTax;
import org.compiere.model.MTaxProvider;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfo;
import org.compiere.util.CCache;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;

/**
 * JPiere Tax Provider
 *
 *  @author  Hideaki Hagiwara（萩原 秀明:h.hagiwara@oss-erp.co.jp）
 *  @version  $Id: JPiereTaxProvider.java,v 1.0 2014/08/20
 *
 */
public class JPiereTaxProvider implements ITaxProvider {

	/**	Logger							*/
	protected transient CLogger	log = CLogger.getCLogger (getClass());

	/**	Cache						*/
	private static CCache<Integer,MBPartner> s_cache	= new CCache<Integer,MBPartner>("C_BPartner", 40, 5);	//	5 minutes


	/*********************************************************************************************************
	 * Calculate Order Tax
	 *
	 */

	@Override
	public boolean calculateOrderTaxTotal(MTaxProvider provider, MOrder order) {
		//	Lines
		BigDecimal totalLines = Env.ZERO;
		ArrayList<Integer> taxList = new ArrayList<Integer>();
		MOrderLine[] lines = order.getLines();
		for (int i = 0; i < lines.length; i++)
		{
			MOrderLine line = lines[i];
			totalLines = totalLines.add(line.getLineNetAmt());
			Integer taxID = new Integer(line.getC_Tax_ID());
			if (!taxList.contains(taxID))
			{
				MTax tax = new MTax(order.getCtx(), taxID, order.get_TrxName());
				if (tax.getC_TaxProvider_ID() == 0)
					continue;
				MOrderTax oTax = MOrderTax.get (line, order.getPrecision(), false, order.get_TrxName());	//	current Tax
				oTax.setIsTaxIncluded(order.isTaxIncluded());
				if (!calculateTaxFromOrderLines(line, oTax))
					return false;
				if (!oTax.save(order.get_TrxName()))
					return false;
				taxList.add(taxID);
			}
		}

		//	Taxes
		BigDecimal grandTotal = totalLines;
		MOrderTax[] taxes = order.getTaxes(true);

		RoundingMode roundingMode = JPiereTaxProvider.getRoundingMode(lines[0].getParent().getC_BPartner_ID(), lines[0].getParent().isSOTrx(), provider);

		for (int i = 0; i < taxes.length; i++)
		{
			MOrderTax oTax = taxes[i];
			if (oTax.getC_TaxProvider_ID() == 0) {
				if (!order.isTaxIncluded())
					grandTotal = grandTotal.add(oTax.getTaxAmt());
				continue;
			}
			MTax tax = MTax.get(oTax.getCtx(), oTax.getC_Tax_ID());
			if (tax.isSummary())
			{
				MTax[] cTaxes = tax.getChildTaxes(false);
				for (int j = 0; j < cTaxes.length; j++)
				{
					MTax cTax = cTaxes[j];
					BigDecimal taxAmt = calculateTax(cTax, oTax.getTaxBaseAmt(), order.isTaxIncluded(), order.getPrecision(), roundingMode);
					//
					MOrderTax newOTax = new MOrderTax(order.getCtx(), 0, order.get_TrxName());
					newOTax.set_ValueOfColumn("AD_Client_ID", order.getAD_Client_ID());
					newOTax.setAD_Org_ID(order.getAD_Org_ID());
					newOTax.setC_Order_ID(order.getC_Order_ID());
					newOTax.setC_Tax_ID(cTax.getC_Tax_ID());
//					newOTax.setPrecision(order.getPrecision());
					newOTax.setIsTaxIncluded(order.isTaxIncluded());
					newOTax.setTaxBaseAmt(oTax.getTaxBaseAmt());
					newOTax.setTaxAmt(taxAmt);
					if (!newOTax.save(order.get_TrxName()))
						return false;
					//
					if (!order.isTaxIncluded())
						grandTotal = grandTotal.add(taxAmt);
				}
				if (!oTax.delete(true, order.get_TrxName()))
					return false;
				if (!oTax.save(order.get_TrxName()))
					return false;
			}
			else
			{
				if (!order.isTaxIncluded())
					grandTotal = grandTotal.add(oTax.getTaxAmt());
			}
		}
		//
		order.setTotalLines(totalLines);
		order.setGrandTotal(grandTotal);
		return true;
	}


	@Override
	public boolean updateHeaderTax(MTaxProvider provider, MOrderLine line)
	{
		//		Update Order Header
		String sql = "UPDATE C_Order i"
			+ " SET TotalLines="
				+ "(SELECT COALESCE(SUM(LineNetAmt),0) FROM C_OrderLine il WHERE i.C_Order_ID=il.C_Order_ID) "
			+ "WHERE C_Order_ID=" + line.getC_Order_ID();
		int no = DB.executeUpdate(sql, line.get_TrxName());
		if (no != 1)
			log.warning("(1) #" + no);

		if (line.isTaxIncluded())
			sql = "UPDATE C_Order i "
				+ " SET GrandTotal=TotalLines "
				+ "WHERE C_Order_ID=" + line.getC_Order_ID();
		else
			sql = "UPDATE C_Order i "
				+ " SET GrandTotal=TotalLines+"
					+ "(SELECT COALESCE(SUM(TaxAmt),0) FROM C_OrderTax it WHERE i.C_Order_ID=it.C_Order_ID) "
					+ "WHERE C_Order_ID=" + line.getC_Order_ID();
		no = DB.executeUpdate(sql, line.get_TrxName());
		if (no != 1)
			log.warning("(2) #" + no);

		line.clearParent();
		return no == 1;
	}


	@Override
	public boolean updateOrderTax(MTaxProvider provider, MOrderLine line) {
    	return  updateOrderTax(line, false);
	}


	@Override
	public boolean recalculateTax(MTaxProvider provider, MOrderLine line, boolean newRecord)
	{
		if (!newRecord && line.is_ValueChanged(MOrderLine.COLUMNNAME_C_Tax_ID) && !line.getParent().isProcessed())
		{
    		if (!updateOrderTax(line,true))
				return false;
		}

		if(!updateOrderTax(line, false))
			return false;

		return updateHeaderTax(provider, line);
	}


	private boolean updateOrderTax(MOrderLine line, boolean oldTax){
		MOrderTax tax = MOrderTax.get (line, line.getPrecision(), oldTax, line.get_TrxName());
		if (tax != null) {
			if (!calculateTaxFromOrderLines(line,tax))
				return false;
			if (tax.getTaxAmt().signum() != 0) {
				if (!tax.save(line.get_TrxName()))
					return false;
			} else {
				if (!tax.is_new() && !tax.delete(false, line.get_TrxName()))
					return false;
			}
		}
		return true;
	}


	private boolean calculateTaxFromOrderLines (MOrderLine line, MOrderTax m_oderTax)
	{
		BigDecimal taxBaseAmt = Env.ZERO;
		BigDecimal taxAmt = Env.ZERO;

		MTax tax = MTax.get(m_oderTax.getCtx(), m_oderTax.getC_Tax_ID());
		boolean documentLevel = tax.isDocumentLevel();

		RoundingMode roundingMode = JPiereTaxProvider.getRoundingMode(line.getParent().getC_BPartner_ID(), line.getParent().isSOTrx(), tax.getC_TaxProvider());

		//
		String sql = "SELECT LineNetAmt FROM C_OrderLine WHERE C_Order_ID=? AND C_Tax_ID=?";
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement (sql, m_oderTax.get_TrxName());
			pstmt.setInt (1,m_oderTax. getC_Order_ID());
			pstmt.setInt (2, m_oderTax.getC_Tax_ID());
			rs = pstmt.executeQuery ();
			while (rs.next ())
			{
				BigDecimal baseAmt = rs.getBigDecimal(1);
				taxBaseAmt = taxBaseAmt.add(baseAmt);
				//
				if (!documentLevel)		// calculate line tax
					taxAmt = taxAmt.add(calculateTax(tax, baseAmt, m_oderTax.isTaxIncluded(), line.getPrecision(), roundingMode));
			}
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, m_oderTax.get_TrxName(), e);
			taxBaseAmt = null;
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		//
		if (taxBaseAmt == null)
			return false;

		//	Calculate Tax
		if (documentLevel)		//	document level
			taxAmt = calculateTax(tax, taxBaseAmt, m_oderTax.isTaxIncluded(), line.getPrecision(), roundingMode);
		m_oderTax.setTaxAmt(taxAmt);

		//	Set Base
		if (m_oderTax.isTaxIncluded())
			m_oderTax.setTaxBaseAmt (taxBaseAmt.subtract(taxAmt));
		else
			m_oderTax.setTaxBaseAmt (taxBaseAmt);
		if (log.isLoggable(Level.FINE)) log.fine(toString());
		return true;
	}	//	calculateTaxFromLines




	/*********************************************************************************************************
	 * Calculate Invoice Tax
	 *
	 */

	@Override
	public boolean calculateInvoiceTaxTotal(MTaxProvider provider, MInvoice invoice) {
		//	Lines
		BigDecimal totalLines = Env.ZERO;
		ArrayList<Integer> taxList = new ArrayList<Integer>();
		MInvoiceLine[] lines = invoice.getLines(false);
		for (int i = 0; i < lines.length; i++)
		{
			MInvoiceLine line = lines[i];
			totalLines = totalLines.add(line.getLineNetAmt());
			if (!taxList.contains(line.getC_Tax_ID()))
			{
				MTax tax = new MTax(invoice.getCtx(), line.getC_Tax_ID(), invoice.get_TrxName());
				if (tax.getC_TaxProvider_ID() == 0)
					continue;
				MInvoiceTax iTax = MInvoiceTax.get (line, invoice.getPrecision(), false, invoice.get_TrxName()); //	current Tax
				if (iTax != null)
				{
					iTax.setIsTaxIncluded(invoice.isTaxIncluded());
					if (!calculateTaxFromInvoiceLines(line,iTax))
						return false;
					iTax.saveEx();
					taxList.add(line.getC_Tax_ID());
				}
			}
		}

		//	Taxes
		BigDecimal grandTotal = totalLines;
		MInvoiceTax[] taxes = invoice.getTaxes(true);

		RoundingMode roundingMode = JPiereTaxProvider.getRoundingMode(lines[0].getParent().getC_BPartner_ID(), lines[0].getParent().isSOTrx(), provider);

		for (int i = 0; i < taxes.length; i++)
		{
			MInvoiceTax iTax = taxes[i];
			if (iTax.getC_TaxProvider_ID() == 0) {
				if (!invoice.isTaxIncluded())
				    grandTotal = grandTotal.add(iTax.getTaxAmt());
		    	continue;
		    }
			MTax tax = MTax.get(iTax.getCtx(), iTax.getC_Tax_ID());
			if (tax.isSummary())
			{
				MTax[] cTaxes = tax.getChildTaxes(false);	//	Multiple taxes
				for (int j = 0; j < cTaxes.length; j++)
				{
					MTax cTax = cTaxes[j];
					BigDecimal taxAmt = calculateTax(cTax, iTax.getTaxBaseAmt(), invoice.isTaxIncluded(), invoice.getPrecision(), roundingMode);
					//
					MInvoiceTax newITax = new MInvoiceTax(invoice.getCtx(), 0, invoice.get_TrxName());
					newITax.set_ValueOfColumn("AD_Client_ID", invoice.getAD_Client_ID());
					newITax.setAD_Org_ID(invoice.getAD_Org_ID());
					newITax.setC_Invoice_ID(invoice.getC_Invoice_ID());
					newITax.setC_Tax_ID(cTax.getC_Tax_ID());
//					newITax.setPrecision(invoice.getPrecision());
					newITax.setIsTaxIncluded(invoice.isTaxIncluded());
					newITax.setTaxBaseAmt(iTax.getTaxBaseAmt());
					newITax.setTaxAmt(taxAmt);
					newITax.saveEx(invoice.get_TrxName());
					//
					if (!invoice.isTaxIncluded())
						grandTotal = grandTotal.add(taxAmt);
				}
				iTax.deleteEx(true, invoice.get_TrxName());
			}
			else
			{
				if (!invoice.isTaxIncluded())
					grandTotal = grandTotal.add(iTax.getTaxAmt());
			}
		}
		//
		invoice.setTotalLines(totalLines);
		invoice.setGrandTotal(grandTotal);
		return true;
	}


	@Override
	public boolean updateHeaderTax(MTaxProvider provider, MInvoiceLine line)
	{
		//		Update Invoice Header
		String sql = "UPDATE C_Invoice i"
			+ " SET TotalLines="
				+ "(SELECT COALESCE(SUM(LineNetAmt),0) FROM C_InvoiceLine il WHERE i.C_Invoice_ID=il.C_Invoice_ID) "
			+ "WHERE C_Invoice_ID=?";
		int no = DB.executeUpdateEx(sql, new Object[]{line.getC_Invoice_ID()}, line.get_TrxName());
		if (no != 1)
			log.warning("(1) #" + no);

		if (line.isTaxIncluded())
			sql = "UPDATE C_Invoice i "
				+ " SET GrandTotal=TotalLines "
				+ "WHERE C_Invoice_ID=?";
		else
			sql = "UPDATE C_Invoice i "
				+ " SET GrandTotal=TotalLines+"
					+ "(SELECT COALESCE(SUM(TaxAmt),0) FROM C_InvoiceTax it WHERE i.C_Invoice_ID=it.C_Invoice_ID) "
					+ "WHERE C_Invoice_ID=?";
		no = DB.executeUpdateEx(sql, new Object[]{line.getC_Invoice_ID()}, line.get_TrxName());
		if (no != 1)
			log.warning("(2) #" + no);
		line.clearParent();

		return no == 1;
	}


	@Override
	public boolean updateInvoiceTax(MTaxProvider provider, MInvoiceLine line) {
			return updateInvoiceTax(line, false);
	}


	@Override
	public boolean recalculateTax(MTaxProvider provider, MInvoiceLine line, boolean newRecord) {
		if (!newRecord && line.is_ValueChanged(MInvoiceLine.COLUMNNAME_C_Tax_ID))
		{
    		if (!updateInvoiceTax(line, true))
				return false;
		}

		if(!updateInvoiceTax(line, false))
			return false;

		return updateHeaderTax(provider, line);
	}


	private boolean updateInvoiceTax(MInvoiceLine line, boolean oldTax){
	    MInvoiceTax tax = MInvoiceTax.get (line, line.getPrecision(), oldTax, line.get_TrxName());
	    if (tax != null) {
	    	if (!calculateTaxFromInvoiceLines(line, tax))
	    		return false;
	    	if (tax.getTaxAmt().signum() != 0) {
	    		if (!tax.save(tax.get_TrxName()))
	    			return false;
	    	} else {
	    		if (!tax.is_new() && !tax.delete(false, tax.get_TrxName()))
	    			return false;
	    	}
		}
	    return true;
	}


	private boolean calculateTaxFromInvoiceLines (MInvoiceLine line, MInvoiceTax m_invoiceTax)
	{
		BigDecimal taxBaseAmt = Env.ZERO;
		BigDecimal taxAmt = Env.ZERO;
		//
		MTax tax = MTax.get(m_invoiceTax.getCtx(), m_invoiceTax.getC_Tax_ID());
		boolean documentLevel = tax.isDocumentLevel();

		RoundingMode roundingMode = JPiereTaxProvider.getRoundingMode(line.getParent().getC_BPartner_ID(), line.getParent().isSOTrx(), tax.getC_TaxProvider());

		//
		String sql = "SELECT il.LineNetAmt, COALESCE(il.TaxAmt,0), i.IsSOTrx "
			+ "FROM C_InvoiceLine il"
			+ " INNER JOIN C_Invoice i ON (il.C_Invoice_ID=i.C_Invoice_ID) "
			+ "WHERE il.C_Invoice_ID=? AND il.C_Tax_ID=?";
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement (sql, m_invoiceTax.get_TrxName());
			pstmt.setInt (1, m_invoiceTax.getC_Invoice_ID());
			pstmt.setInt (2, m_invoiceTax.getC_Tax_ID());
			rs = pstmt.executeQuery ();
			while (rs.next ())
			{
				//	BaseAmt
				BigDecimal baseAmt = rs.getBigDecimal(1);
				taxBaseAmt = taxBaseAmt.add(baseAmt);
				//	TaxAmt
				BigDecimal amt = rs.getBigDecimal(2);
				if (amt == null)
					amt = Env.ZERO;
				boolean isSOTrx = "Y".equals(rs.getString(3));
				//
				// phib [ 1702807 ]: manual tax should never be amended
				// on line level taxes
				if (!documentLevel && amt.signum() != 0 && !isSOTrx)	//	manually entered
					;
				else if (documentLevel || baseAmt.signum() == 0)
					amt = Env.ZERO;
				else	// calculate line tax
					amt = calculateTax(tax, baseAmt, m_invoiceTax.isTaxIncluded(), line.getPrecision(),roundingMode);
				//
				taxAmt = taxAmt.add(amt);
			}
		}
		catch (SQLException e)
		{
			throw new DBException(e, sql);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null; pstmt = null;
		}

		//	Calculate Tax
		if (documentLevel || taxAmt.signum() == 0)
			taxAmt = calculateTax(tax, taxBaseAmt, m_invoiceTax.isTaxIncluded(), line.getPrecision(), roundingMode);
		m_invoiceTax.setTaxAmt(taxAmt);

		//	Set Base
		if (m_invoiceTax.isTaxIncluded())
			m_invoiceTax.setTaxBaseAmt (taxBaseAmt.subtract(taxAmt));
		else
			m_invoiceTax.setTaxBaseAmt (taxBaseAmt);
		return true;
	}	//	calculateTaxFromLines




	/*********************************************************************************************************
	 * Calculate RMA Tax
	 *
	 */

	@Override
	public boolean calculateRMATaxTotal(MTaxProvider provider, MRMA rma) {
		//	Lines
		BigDecimal totalLines = Env.ZERO;
		ArrayList<Integer> taxList = new ArrayList<Integer>();
		MRMALine[] lines = rma.getLines(false);
		for (int i = 0; i < lines.length; i++)
		{
			MRMALine line = lines[i];
			totalLines = totalLines.add(line.getLineNetAmt());
			Integer taxID = new Integer(line.getC_Tax_ID());
			if (!taxList.contains(taxID))
			{
				MTax tax = new MTax(rma.getCtx(), taxID, rma.get_TrxName());
				if (tax.getC_TaxProvider_ID() == 0)
					continue;
				MRMATax oTax = MRMATax.get (line, rma.getPrecision(), false, rma.get_TrxName());	//	current Tax
				oTax.setIsTaxIncluded(rma.isTaxIncluded());
				if (!calculateTaxFromRMALines(line, oTax))
					return false;
				if (!oTax.save(rma.get_TrxName()))
					return false;
				taxList.add(taxID);
			}
		}

		//	Taxes
		BigDecimal grandTotal = totalLines;
		MRMATax[] taxes = rma.getTaxes(true);

		RoundingMode roundingMode = JPiereTaxProvider.getRoundingMode(lines[0].getParent().getC_BPartner_ID(), lines[0].getParent().isSOTrx(), provider);

		for (int i = 0; i < taxes.length; i++)
		{
			MRMATax oTax = taxes[i];
			if (oTax.getC_TaxProvider_ID() == 0) {
				if (!rma.isTaxIncluded())
					grandTotal = grandTotal.add(oTax.getTaxAmt());
				continue;
			}
			MTax tax = MTax.get(oTax.getCtx(), oTax.getC_Tax_ID());
			if (tax.isSummary())
			{
				MTax[] cTaxes = tax.getChildTaxes(false);
				for (int j = 0; j < cTaxes.length; j++)
				{
					MTax cTax = cTaxes[j];
					BigDecimal taxAmt = calculateTax(cTax, oTax.getTaxBaseAmt(), rma.isTaxIncluded(), rma.getPrecision(), roundingMode );
					//
					MRMATax newOTax = new MRMATax(rma.getCtx(), 0, rma.get_TrxName());
					newOTax.set_ValueOfColumn("AD_Client_ID", rma.getAD_Client_ID());
					newOTax.setAD_Org_ID(rma.getAD_Org_ID());
					newOTax.setM_RMA_ID(rma.getM_RMA_ID());
					newOTax.setC_Tax_ID(cTax.getC_Tax_ID());
//					newOTax.setPrecision(rma.getPrecision());
					newOTax.setIsTaxIncluded(rma.isTaxIncluded());
					newOTax.setTaxBaseAmt(oTax.getTaxBaseAmt());
					newOTax.setTaxAmt(taxAmt);
					if (!newOTax.save(rma.get_TrxName()))
						return false;
					//
					if (!rma.isTaxIncluded())
						grandTotal = grandTotal.add(taxAmt);
				}
				if (!oTax.delete(true, rma.get_TrxName()))
					return false;
				if (!oTax.save(rma.get_TrxName()))
					return false;
			}
			else
			{
				if (!rma.isTaxIncluded())
					grandTotal = grandTotal.add(oTax.getTaxAmt());
			}
		}
		//
		rma.setAmt(grandTotal);
		return true;
	}


	@Override
	public boolean updateHeaderTax(MTaxProvider provider, MRMALine line)
	{
		//	Update RMA Header
		String sql = "UPDATE M_RMA "
			+ " SET Amt="
				+ "(SELECT COALESCE(SUM(LineNetAmt),0) FROM M_RMALine WHERE M_RMA.M_RMA_ID=M_RMALine.M_RMA_ID) "
			+ "WHERE M_RMA_ID=?";
		int no = DB.executeUpdateEx(sql, new Object[]{line.getM_RMA_ID()}, line.get_TrxName());
		if (no != 1)
			log.warning("(1) #" + no);

		line.clearParent();

		return no == 1;
	}


	@Override
	public boolean updateRMATax(MTaxProvider provider, MRMALine line) {
		return updateRMATax(line, false);

	}


	@Override
	public boolean recalculateTax(MTaxProvider provider, MRMALine line, boolean newRecord)
	{
		if (!newRecord && line.is_ValueChanged(MRMALine.COLUMNNAME_C_Tax_ID) && !line.getParent().isProcessed())
		{
			if (!updateRMATax(line, true))
				return false;
		}

		if (!updateRMATax(line, false))
			return false;

        return updateHeaderTax(provider, line);
	}


	private boolean updateRMATax(MRMALine line, boolean oldTax){
		MRMATax tax = MRMATax.get (line, line.getPrecision(), oldTax, line.get_TrxName());
		if (tax != null)
		{
			if (!calculateTaxFromRMALines(line, tax))
				return false;
			if (tax.getTaxAmt().signum() != 0)
			{
				if (!tax.save(tax.get_TrxName()))
					return false;
			}
			else
			{
				if (!tax.is_new() && !tax.delete(false, tax.get_TrxName()))
					return false;
			}
		}
		return true;
	}


	private boolean calculateTaxFromRMALines (MRMALine line, MRMATax m_rmatax)
	{
		BigDecimal taxBaseAmt = Env.ZERO;
		BigDecimal taxAmt = Env.ZERO;

		MTax tax = MTax.get(m_rmatax.getCtx(), m_rmatax.getC_Tax_ID());
		boolean documentLevel = tax.isDocumentLevel();

		RoundingMode roundingMode = JPiereTaxProvider.getRoundingMode(line.getParent().getC_BPartner_ID(), line.getParent().isSOTrx(), tax.getC_TaxProvider());

		String sql = "SELECT LineNetAmt FROM M_RMALine WHERE M_RMA_ID=? AND C_Tax_ID=?";
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement (sql, line.get_TrxName());
			pstmt.setInt (1, m_rmatax.getM_RMA_ID());
			pstmt.setInt (2, m_rmatax.getC_Tax_ID());
			rs = pstmt.executeQuery ();
			while (rs.next ())
			{
				BigDecimal baseAmt = rs.getBigDecimal(1);
				taxBaseAmt = taxBaseAmt.add(baseAmt);
				//
				if (!documentLevel)		// calculate line tax
					taxAmt = taxAmt.add(calculateTax(tax, baseAmt, m_rmatax.isTaxIncluded(), line.getPrecision(), roundingMode));
			}
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, line.get_TrxName(), e);
			taxBaseAmt = null;
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		//
		if (taxBaseAmt == null)
			return false;

		//	Calculate Tax
		if (documentLevel)		//	document level
			taxAmt = calculateTax(tax, taxBaseAmt, m_rmatax.isTaxIncluded(), line.getPrecision(), roundingMode);
		m_rmatax.setTaxAmt(taxAmt);

		//	Set Base
		if (m_rmatax.isTaxIncluded())
			m_rmatax.setTaxBaseAmt (taxBaseAmt.subtract(taxAmt));
		else
			m_rmatax.setTaxBaseAmt (taxBaseAmt);
		if (log.isLoggable(Level.FINE)) log.fine(toString());
		return true;
	}	//	calculateTaxFromRMALines


	/*********************************************************************************************************
	 * Other Method
	 *
	 */


	@Override
	public String validateConnection(MTaxProvider provider, ProcessInfo pi) throws Exception {
		throw new IllegalStateException(Msg.getMsg(provider.getCtx(), "ActionNotSupported"));
	}

	private static RoundingMode getRoundingMode(int C_BPartner_ID, boolean isSOTrx, I_C_TaxProvider provider)
	{
		Integer key = new Integer (C_BPartner_ID);
		MBPartner bp = (MBPartner) s_cache.get (key);
		if (bp == null){
			bp = MBPartner.get(Env.getCtx(), C_BPartner_ID);
			if(bp == null){ //TODO:Mobile-UI対応(Mobile-UIではCtxからAD_Client_IDが取得できない事で取引先がNullになるため)
				String whereClause = "C_BPartner_ID=? AND AD_Client_ID=?";
				bp = new Query(Env.getCtx(),I_C_BPartner.Table_Name,whereClause,null)
				.setParameters(C_BPartner_ID,provider.getAD_Client_ID())
				.firstOnly();
			}
		}
		if (bp.get_ID () != 0)
			s_cache.put (key, bp);

		RoundingMode roundingMode = null;//TODO

		if(isSOTrx){
			Object SO_TaxRounding = bp.get_Value("JP_SOTaxRounding");
			if(SO_TaxRounding != null)
				roundingMode = RoundingMode.valueOf(new Integer(SO_TaxRounding.toString()).intValue());
		}else{
			Object PO_TaxRounding = bp.get_Value("JP_POTaxRounding");
			if(PO_TaxRounding != null)
				roundingMode = RoundingMode.valueOf(new Integer(PO_TaxRounding.toString()).intValue());
		}

		if(roundingMode == null){

			if(provider != null && provider.getAccount() != null){
				String roundingModeString = provider.getAccount();
				if(roundingModeString.equals("UP"))
					return RoundingMode.UP;
				else if(roundingModeString.equals("DOWN"))
					return RoundingMode.DOWN;
				else if(roundingModeString.equals("CEILING"))
					return RoundingMode.CEILING;
				else if(roundingModeString.equals("FLOOR"))
					return RoundingMode.FLOOR;
				else if(roundingModeString.equals("HALF_UP"))
					return RoundingMode.HALF_UP;
				else if(roundingModeString.equals("HALF_DOWN"))
					return RoundingMode.HALF_DOWN;
				else if(roundingModeString.equals("HALF_EVEN"))
					return RoundingMode.HALF_EVEN;
				else if(roundingModeString.equals("UNNECESSARY"))
					return RoundingMode.UNNECESSARY;
				else
					return RoundingMode.DOWN;
			}

			roundingMode = RoundingMode.DOWN;
		}

		return roundingMode;
	}


	private BigDecimal calculateTax (MTax m_tax, BigDecimal amount, boolean taxIncluded, int scale, RoundingMode roundingMode)
	{
		//	Null Tax
		if (m_tax.isZeroTax())
			return Env.ZERO;


		BigDecimal multiplier = m_tax.getRate().divide(Env.ONEHUNDRED, 12, RoundingMode.HALF_UP);

		BigDecimal tax = null;
		if (!taxIncluded)	//	$100 * 6 / 100 == $6 == $100 * 0.06
		{
			tax = amount.multiply (multiplier);
		}
		else			//	$106 - ($106 / (100+6)/100) == $6 == $106 - ($106/1.06)
		{
			multiplier = multiplier.add(Env.ONE);
			BigDecimal base = amount.divide(multiplier, 12, RoundingMode.HALF_UP);
			tax = amount.subtract(base);
		}
		BigDecimal finalTax = tax.setScale(scale, roundingMode);
		if (log.isLoggable(Level.FINE)) log.fine("calculateTax " + amount
			+ " (incl=" + taxIncluded + ",mult=" + multiplier + ",scale=" + scale
			+ ") = " + finalTax + " [" + tax + "]");
		return finalTax;
	}	//	calculateTax
}