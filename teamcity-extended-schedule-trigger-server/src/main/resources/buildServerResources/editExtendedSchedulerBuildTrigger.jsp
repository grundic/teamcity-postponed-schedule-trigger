<%@ page import="jetbrains.buildServer.buildTriggers.scheduler.CronFieldInfo" %>
<%@ page import="jetbrains.buildServer.util.Dates" %>
<%@ include file="/include.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>
<jsp:useBean id="propertiesBean" type="jetbrains.buildServer.controllers.BasePropertiesBean" scope="request"/>


<jsp:include page="/admin/triggers/editSchedulingTrigger.jsp"/>