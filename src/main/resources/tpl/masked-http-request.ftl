<#ftl output_format="HTML">
<#-- @ftlvariable name="data" type="io.qameta.allure.attachment.http.HttpRequestAttachment" -->
<#--
  allure-attachments 2.35.5 tpl/http-request.ftl with secrets masked in the body and the curl command
  (e.g. the sign-up password). Same rule as ApiLoggingFilter.SECRET_FIELD: change both together.
  Used by ApiClient through AllureRestAssured.setRequestTemplate().
-->
<#function mask text>
    <#return text?replace(r'("?\b(?:password\w*|\w*token)"?\s*[:=]\s*)("(?:[^"\\]|\\.)*"|[^,)}\s]+)', '$1"***"', 'ri')>
</#function>
<div><#if data.method??>${data.method}<#else>GET</#if> to <#if data.url??>${data.url}<#else>Unknown</#if></div>

<#if data.body??>
    <h4>Body</h4>
    <div>
    <pre class="preformated-text">
    <#t>${mask(data.body)}
    </pre>
    </div>
</#if>

<#if (data.headers)?has_content>
    <h4>Headers</h4>
    <div>
        <#list data.headers as name, value>
            <div>${name}: ${value!"null"}</div>
        </#list>
    </div>
</#if>


<#if (data.cookies)?has_content>
    <h4>Cookies</h4>
    <div>
        <#list data.cookies as name, value>
            <div>${name}: ${value!"null"}</div>
        </#list>
    </div>
</#if>

<#if data.curl??>
    <h4>Curl</h4>
    <div>
        ${mask(data.curl)}
    </div>
</#if>

<#if (data.formParams)?has_content>
    <h4>FormParams</h4>
    <div>
        <#list data.formParams as name, value>
            <div>${name}: ${value!"null"}</div>
        </#list>
    </div>
</#if>
