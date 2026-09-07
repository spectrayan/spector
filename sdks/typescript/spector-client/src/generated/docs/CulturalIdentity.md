
# CulturalIdentity


## Properties

Name | Type
------------ | -------------
`ethnicity` | string
`race` | string
`religion` | string
`culturalHeritage` | string
`culturalValues` | Array&lt;string&gt;
`traditions` | Array&lt;string&gt;
`primaryCulture` | string
`cultureEmbedding` | Array&lt;number&gt;
`present` | boolean

## Example

```typescript
import type { CulturalIdentity } from ''

// TODO: Update the object below with actual values
const example = {
  "ethnicity": null,
  "race": null,
  "religion": null,
  "culturalHeritage": null,
  "culturalValues": null,
  "traditions": null,
  "primaryCulture": null,
  "cultureEmbedding": null,
  "present": null,
} satisfies CulturalIdentity

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CulturalIdentity
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


