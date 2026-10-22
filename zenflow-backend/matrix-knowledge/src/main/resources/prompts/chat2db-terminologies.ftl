<#list terminologies as terminology>
    <terminology>
        <words>
            <word>${terminology.word}</word>
            <#list terminology.synonymsList as synonyms>
                <word>${synonyms}</word>
            </#list>
        </words>
        <description>${terminology.description}</description>
    </terminology>
</#list>
