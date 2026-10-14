<#--
  Masks secret JSON fields and key=value pairs, for masked-http-request.ftl and masked-http-response.ftl.
  Same rule as ApiLoggingFilter.SECRET_FIELD: change both together.
-->
<#function mask text>
    <#return text?replace(r'("?\b(?:password\w*|\w*token)"?\s*[:=]\s*)("(?:[^"\\]|\\.)*"|[^,)}\s]+)', '$1"***"', 'ri')>
</#function>
