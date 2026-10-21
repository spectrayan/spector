
# MelSchemaResponse


## Properties

Name | Type
------------ | -------------
`keywords` | Array&lt;string&gt;
`tiers` | Array&lt;string&gt;
`sources` | Array&lt;string&gt;
`clauseTemplates` | [Array&lt;MelClauseTemplate&gt;](MelClauseTemplate.md)
`snippets` | [Array&lt;MelSnippetTemplate&gt;](MelSnippetTemplate.md)
`snippetLibrary` | [Array&lt;MelSnippetTemplate&gt;](MelSnippetTemplate.md)

## Example

```typescript
import type { MelSchemaResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "keywords": null,
  "tiers": null,
  "sources": null,
  "clauseTemplates": null,
  "snippets": null,
  "snippetLibrary": null,
} satisfies MelSchemaResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as MelSchemaResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


