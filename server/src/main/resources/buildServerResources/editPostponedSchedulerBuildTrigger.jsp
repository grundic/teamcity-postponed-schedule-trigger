<%@ include file="/include.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>


<jsp:include page="/admin/triggers/editSchedulingTrigger.jsp"/>
<l:settingsGroup title="Postponed parameters"/>
<tr>
    <td><label for="buildTypeIdList">Build ids to wait for:</label></td>
    <td>
        <props:textProperty id="buildTypeIdList" name="buildTypeIdList"/>
        <span class="smallNote">List of buildId, delimited by ';'</span>
        <span class="error" id="error_buildTypeIdList"></span>
    </td>
</tr>

<tr>
    <td><label for="waitTimeout">Time to wait for builds:</label></td>
    <td>
        <props:textProperty id="waitTimeout" name="waitTimeout"/>
        <span class="smallNote">Time in minutes to wait for running/queued builds</span>
        <span class="error" id="error_waitTimeout"></span>
    </td>
</tr>