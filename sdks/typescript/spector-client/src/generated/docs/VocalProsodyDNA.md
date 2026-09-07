
# VocalProsodyDNA


## Properties

Name | Type
------------ | -------------
`baselineF0Hz` | number
`f0Variance` | number
`baselineWordsPerMinute` | number
`vocalTension` | number
`accent` | string
`voiceId` | string
`modulationMap` | [AcousticModulationMap](AcousticModulationMap.md)
`present` | boolean

## Example

```typescript
import type { VocalProsodyDNA } from ''

// TODO: Update the object below with actual values
const example = {
  "baselineF0Hz": null,
  "f0Variance": null,
  "baselineWordsPerMinute": null,
  "vocalTension": null,
  "accent": null,
  "voiceId": null,
  "modulationMap": null,
  "present": null,
} satisfies VocalProsodyDNA

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as VocalProsodyDNA
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


