
# PersonaContext


## Properties

Name | Type
------------ | -------------
`about` | string
`occupation` | string
`education` | [Array&lt;Education&gt;](Education.md)
`nationality` | string
`languages` | Array&lt;string&gt;
`culturalIdentity` | [CulturalIdentity](CulturalIdentity.md)
`bigFive` | [BigFiveTraits](BigFiveTraits.md)
`emotionalIntelligence` | [EmotionalIntelligence](EmotionalIntelligence.md)
`stressResponse` | string
`values` | Array&lt;string&gt;
`fears` | Array&lt;string&gt;
`aspirations` | Array&lt;string&gt;
`communicationStyle` | string
`idiolect` | [IdiolectProfile](IdiolectProfile.md)
`vocalProsody` | [VocalProsodyDNA](VocalProsodyDNA.md)
`embodiedKinesics` | [EmbodiedKinesicsDNA](EmbodiedKinesicsDNA.md)
`modifiers` | [PersonalityModifiers](PersonalityModifiers.md)
`aboutEmbedding` | Array&lt;number&gt;
`occupationEmbedding` | Array&lt;number&gt;
`educationEmbedding` | Array&lt;number&gt;
`valuesEmbedding` | Array&lt;number&gt;
`aspirationsEmbedding` | Array&lt;number&gt;
`present` | boolean

## Example

```typescript
import type { PersonaContext } from ''

// TODO: Update the object below with actual values
const example = {
  "about": null,
  "occupation": null,
  "education": null,
  "nationality": null,
  "languages": null,
  "culturalIdentity": null,
  "bigFive": null,
  "emotionalIntelligence": null,
  "stressResponse": null,
  "values": null,
  "fears": null,
  "aspirations": null,
  "communicationStyle": null,
  "idiolect": null,
  "vocalProsody": null,
  "embodiedKinesics": null,
  "modifiers": null,
  "aboutEmbedding": null,
  "occupationEmbedding": null,
  "educationEmbedding": null,
  "valuesEmbedding": null,
  "aspirationsEmbedding": null,
  "present": null,
} satisfies PersonaContext

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PersonaContext
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


