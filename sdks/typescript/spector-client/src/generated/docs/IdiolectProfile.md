
# IdiolectProfile


## Properties

Name | Type
------------ | -------------
`stylometrics` | [StylometricFeatures](StylometricFeatures.md)
`lexicon` | [IdiosyncraticLexicon](IdiosyncraticLexicon.md)
`rhetoric` | [RhetoricalPatterns](RhetoricalPatterns.md)
`primaryLanguage` | string
`idiolectEmbedding` | Array&lt;number&gt;
`present` | boolean

## Example

```typescript
import type { IdiolectProfile } from ''

// TODO: Update the object below with actual values
const example = {
  "stylometrics": null,
  "lexicon": null,
  "rhetoric": null,
  "primaryLanguage": null,
  "idiolectEmbedding": null,
  "present": null,
} satisfies IdiolectProfile

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as IdiolectProfile
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


