<template>
  <component :is="type" v-bind="linkProps(to)">
    <slot />
  </component>
</template>

<script>
import { isExternal } from '@/utils/validate'

export default {
  props: {
    to: {
      type: [String, Object],
      required: true
    }
  },
  computed: {
    isExternal() {
      return isExternal(this.to)
    },
    type() {
      if (this.isExternal) {
        return 'a'
      }
      return 'router-link'
    },
    isNewPage(){
        return this.to === "/courseevaluate";
     }
  },
  methods: {
    linkProps(to) {
      if (this.isExternal) {
        return {
          href: to,
          target: '_blank',
          rel: 'noopener'
        }
      }
      if(this.isNewPage)
      {
        return {
        to: to.replace("/courseevaluate","/remark"),
        target:'_blank',
        rel: 'noopener'
        }
      }
      return {
        to: to
      }
    }
  }
}
</script>
